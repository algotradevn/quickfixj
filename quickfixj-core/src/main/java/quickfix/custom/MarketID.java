package quickfix.custom;

import quickfix.StringField;

public class MarketID extends StringField {

	private static final long serialVersionUID = 4207434917763442870L;

	public final static int FIELD = 30001;

	public MarketID() {
		super(FIELD);
	}

	public MarketID(final String data) {
		super(FIELD, data);
	}

}
