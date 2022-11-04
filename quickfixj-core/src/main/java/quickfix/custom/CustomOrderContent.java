package quickfix.custom;

import quickfix.StringField;

public class CustomOrderContent extends StringField {

	private static final long serialVersionUID = 6147837376828160241L;

	public final static int FIELD = 6003;

	public CustomOrderContent() {
		super(FIELD);
	}

	public CustomOrderContent(final String data) {
		super(FIELD, data);
	}

}
