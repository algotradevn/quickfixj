package quickfix.custom;

import quickfix.StringField;

public class ThirdPartyBroker extends StringField {

	private static final long serialVersionUID = 2146466373360979152L;

	public static final int FIELD = 6002;

	public ThirdPartyBroker() {
		super(FIELD);
	}

	public ThirdPartyBroker(final String data) {
		super(FIELD, data);
	}

}
