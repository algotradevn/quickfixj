package quickfix.custom;

import quickfix.StringField;

public class ThirdPartySource extends StringField {

	private static final long serialVersionUID = 2146466373360979152L;

	public static final int FIELD = 6002;

	public ThirdPartySource() {
		super(FIELD);
	}

	public ThirdPartySource(final String data) {
		super(FIELD, data);
	}

}
