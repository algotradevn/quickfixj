/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix.mina.message;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.demux.MessageDecoder;
import org.apache.mina.filter.codec.demux.MessageDecoderResult;
import org.quickfixj.CharsetSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.mina.CriticalProtocolCodecException;

/**
 * Detects and decodes FIX message strings in an incoming data stream. The
 * message string is then passed to MINA IO handlers for further processing.
 */
public class FIXMessageDecoder implements MessageDecoder {

	private static final char SOH = '\001';

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final PatternMatcher HEADER_PATTERN;
	private final PatternMatcher HNX_HEADER_PATTERN;
	private final PatternMatcher CHECKSUM_PATTERN;
	private final PatternMatcher LOGON_PATTERN;

	// Parsing states
	private static final int SEEKING_HEADER = 1;
	private static final int PARSING_LENGTH = 2;
	private static final int READING_BODY = 3;
	private static final int PARSING_CHECKSUM = 4;

	// If QFJ receives more garbage data than this between messages, then
	// the connection is considered corrupt.
	private static final int MAX_UNDECODED_DATA_LENGTH = 4096;

	private int state;
	private int bodyLength;
	private int position;
	private final String charsetEncoding;

	private void resetState() {
		state = SEEKING_HEADER;
		bodyLength = 0;
		position = 0;
	}

	public FIXMessageDecoder() throws UnsupportedEncodingException {
		this(CharsetSupport.getCharset());
	}

	public FIXMessageDecoder(final String charset) throws UnsupportedEncodingException {
		this(charset, String.valueOf(SOH));
	}

	public FIXMessageDecoder(final String charset, final String delimiter)
			throws UnsupportedEncodingException {
		charsetEncoding = CharsetSupport.validate(charset);
		HEADER_PATTERN = new PatternMatcher("8=FIXt.?.?" + delimiter + "9=");
		HNX_HEADER_PATTERN = new PatternMatcher("8=HNX.TDS.?" + delimiter + "9=");
		CHECKSUM_PATTERN = new PatternMatcher("10=???" + delimiter);
		LOGON_PATTERN = new PatternMatcher(delimiter + "35=A" + delimiter);
		resetState();
	}

	@Override
	public MessageDecoderResult decodable(final IoSession session, final IoBuffer in) {
		final boolean hasHeader = HEADER_PATTERN.find(in, in.position()) != -1L;
		final boolean hasHNXHeader = HNX_HEADER_PATTERN.find(in, in.position()) != -1L;
		return hasHeader || hasHNXHeader ? MessageDecoderResult.OK
				: in.remaining() > MAX_UNDECODED_DATA_LENGTH ? MessageDecoderResult.NOT_OK
						: MessageDecoderResult.NEED_DATA;
	}

	@Override
	public MessageDecoderResult decode(final IoSession session, final IoBuffer in,
			final ProtocolDecoderOutput out) throws ProtocolCodecException {
		int messageCount = 0;
		while (parseMessage(in, out)) {
			messageCount++;
		}
		if (messageCount > 0) {
			// Mina will compact the buffer because we can't detect a header
			if (state == SEEKING_HEADER) {
				position = 0;
			}
			return MessageDecoderResult.OK;
		} else {
			// Mina will compact the buffer
			position -= in.position();
			return MessageDecoderResult.NEED_DATA;
		}
	}

	/**
	 * This method cannot move the buffer position until a message is found or
	 * an error has occurred. Otherwise, MINA will compact the buffer and we
	 * lose data.
	 */
	private boolean parseMessage(final IoBuffer in, final ProtocolDecoderOutput out)
			throws ProtocolCodecException {
		try {
			boolean messageFound = false;
			while (in.hasRemaining() && !messageFound) {
				if (state == SEEKING_HEADER) {

					final long normalHeaderPos = HEADER_PATTERN.find(in, position);
					final long hnxHeaderPos = HNX_HEADER_PATTERN.find(in, position);
					final long headerPos = normalHeaderPos != -1L ? normalHeaderPos : hnxHeaderPos;
					if (headerPos == -1L) {
						break;
					}
					final int headerOffset = (int) headerPos;
					final int headerLength = (int) (headerPos >>> 32);
					in.position(headerOffset);

					if (log.isDebugEnabled()) {
						log.debug("detected header: {}", getBufferDebugInfo(in));
					}

					position = headerOffset + headerLength;
					state = PARSING_LENGTH;
				}

				if (state == PARSING_LENGTH) {
					byte ch = 0;
					while (position < in.limit()) { // while data remains
						ch = in.get(position++);
						if (ch < '0' || ch > '9') { // if not digit
							if (bodyLength == 0) { // QFJ-903 - we started to
								// parse length but
								// encountered no digit
								handleError(in, in.position() + 1,
										"Encountered invalid body length: " + (char) ch, false);
							}
							break;
						}
						bodyLength = bodyLength * 10 + ch - '0';
					}
					if (state == SEEKING_HEADER) {
						continue;
					}
					if (ch == SOH) {
						state = READING_BODY;
						if (log.isDebugEnabled()) {
							log.debug("body length = {}: {}", bodyLength, getBufferDebugInfo(in));
						}
					} else {
						if (position < in.limit()) { // if data remains
							final String messageString = getMessageStringForError(in);
							handleError(in, position,
									"Length format error in message (last character: " + (char) ch
									+ "): " + messageString,
									false);
							continue;
						} else {
							break;
						}
					}
				}

				if (state == READING_BODY) {
					if (in.limit() - position < bodyLength) { // if remaining
						// data is less
						// than body
						break;
					}
					position += bodyLength;
					state = PARSING_CHECKSUM;
					if (log.isDebugEnabled()) {
						log.debug("message body found: {}", getBufferDebugInfo(in));
					}
				}

				if (state == PARSING_CHECKSUM) {
					// if (CHECKSUM_PATTERN.match(in, position) > 0) {
					// // we are trying to parse the checksum but should
					// // check if the CHECKSUM_PATTERN is preceded by SOH
					// // or if the pattern just occurs inside of another field
					// if (in.get(position - 1) != SOH) {
					// handleError(in, position,
					// "checksum field not preceded by SOH, bad length?",
					// isLogon(in));
					// continue;
					// }
					// if (log.isDebugEnabled()) {
					// log.debug("found checksum: {}", getBufferDebugInfo(in));
					// }
					// position += CHECKSUM_PATTERN.getMinLength();
					// } else {
					// if (position + CHECKSUM_PATTERN.getMinLength() <=
					// in.limit()) {
					// // FEATURE allow configurable recovery position
					// // int recoveryPosition = in.position() + 1;
					// // Following recovery position is compatible with
					// // QuickFIX C++
					// // but drops messages unnecessarily in corruption
					// // scenarios.
					// final int recoveryPosition = position + 1;
					// handleError(in, recoveryPosition,
					// "did not find checksum field, bad length?", isLogon(in));
					// continue;
					// } else {
					// break;
					// }
					// }
					final String messageString = getMessageString(in);
					if (log.isDebugEnabled()) {
						log.debug("parsed message: {} {}", getBufferDebugInfo(in), messageString);
					}
					out.write(messageString); // eventually invokes
					// AbstractIoHandler.messageReceived
					state = SEEKING_HEADER;
					bodyLength = 0;
					messageFound = true;
				}
			}
			return messageFound;
		} catch (final Throwable t) {
			resetState();
			if (t instanceof ProtocolCodecException) {
				throw (ProtocolCodecException) t;
			} else {
				throw new ProtocolCodecException(t);
			}
		}
	}

	private String getBufferDebugInfo(final IoBuffer in) {
		return "pos=" + in.position() + ",lim=" + in.limit() + ",rem=" + in.remaining() + ",offset="
				+ position + ",state=" + state;
	}

	private String getMessageString(final IoBuffer buffer) throws UnsupportedEncodingException {
		final byte[] data = new byte[position - buffer.position()];
		buffer.get(data);
		return new String(data, charsetEncoding);
	}

	private String getMessageStringForError(final IoBuffer buffer)
			throws UnsupportedEncodingException {
		final int initialPosition = buffer.position();
		final byte[] data = new byte[buffer.limit() - initialPosition];
		buffer.get(data);
		buffer.position(position - initialPosition);
		return new String(data, charsetEncoding);
	}

	private void handleError(final IoBuffer buffer, final int recoveryPosition, final String text,
			final boolean disconnect) throws ProtocolCodecException {
		buffer.position(recoveryPosition);
		position = recoveryPosition;
		state = SEEKING_HEADER;
		bodyLength = 0;
		if (disconnect) {
			throw new CriticalProtocolCodecException(text);
		} else {
			log.error(text);
		}
	}

	private boolean isLogon(final IoBuffer buffer) {
		return LOGON_PATTERN.find(buffer, buffer.position()) != -1L;
	}

	@Override
	public void finishDecode(final IoSession session, final ProtocolDecoderOutput out)
			throws Exception {
		// empty
	}

	/**
	 * Used to process streamed messages from a file
	 */
	public interface MessageListener {
		void onMessage(String message);
	}

	/**
	 * Utility method to extract messages from files. This method loads all
	 * extracted messages into memory so if the expected number of extracted
	 * messages is large, do not use this method or your application may run out
	 * of memory. Use the streaming version of the method instead.
	 *
	 * @param file
	 * @return a list of extracted messages
	 * @throws IOException
	 * @throws ProtocolCodecException
	 * @see #extractMessages(File,
	 *      quickfix.mina.message.FIXMessageDecoder.MessageListener)
	 */
	public List<String> extractMessages(final File file)
			throws IOException, ProtocolCodecException {
		final List<String> messages = new ArrayList<>();
		extractMessages(file, messages::add);
		return messages;
	}

	/**
	 * Utility to extract messages from a file. This method will return each
	 * message found to a provided listener. The message file will also be
	 * memory mapped rather than fully loaded into physical memory. Therefore, a
	 * large message file can be processed without using excessive memory.
	 *
	 * @param file
	 * @param listener
	 * @throws IOException
	 * @throws ProtocolCodecException
	 */
	public void extractMessages(final File file, final MessageListener listener)
			throws IOException, ProtocolCodecException {
		// Set up a read-only memory-mapped file
		try (RandomAccessFile fileIn = new RandomAccessFile(file, "r")) {
			final FileChannel readOnlyChannel = fileIn.getChannel();
			final MappedByteBuffer memoryMappedBuffer = readOnlyChannel
					.map(FileChannel.MapMode.READ_ONLY, 0, (int) readOnlyChannel.size());
			decode(null, IoBuffer.wrap(memoryMappedBuffer), new ProtocolDecoderOutput() {
				@Override
				public void write(final Object message) {
					listener.onMessage((String) message);
				}

				@Override
				public void flush(final IoFilter.NextFilter nextFilter, final IoSession ioSession) {
					// ignored
				}
			});
		}
	}

}
