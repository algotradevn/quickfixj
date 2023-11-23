package quickfix.custom;

import quickfix.StringField;

public class BoardID extends StringField {

	private static final long serialVersionUID = 4207434917763442870L;

	public final static int FIELD = 20004;

	public BoardID() {
		super(FIELD);
	}

	public BoardID(final String data) {
		super(FIELD, data);
	}

}
