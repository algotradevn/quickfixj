package quickfix.custom;

import quickfix.CharField;

public class InternalOrderStatus extends CharField {

	private static final long serialVersionUID = -852429746222653260L;

	public final static int FIELD = 6001;

	public static final char INTERNAL_BROKER_OK = '0';
	public static final char THIRD_PARTY_OK = '1';
	public static final char STOCK_EXCHANGE_OK = '2';

	public InternalOrderStatus() {
		super(FIELD);
	}

	public InternalOrderStatus(final char data) {
		super(FIELD, data);
	}

}
