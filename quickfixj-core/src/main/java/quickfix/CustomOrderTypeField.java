package quickfix;

public class CustomOrderTypeField extends StringField {

	private static final long serialVersionUID = 4897852370001093436L;

	private final static int FIELD_ID = 6000;

	public CustomOrderTypeField() {
		super(FIELD_ID);
	}

	public CustomOrderTypeField(final String data) {
		super(FIELD_ID, data);
	}

}
