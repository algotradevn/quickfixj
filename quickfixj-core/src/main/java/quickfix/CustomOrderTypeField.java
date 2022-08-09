package quickfix;

public class CustomOrderTypeField extends StringField {

	private static final long serialVersionUID = 4897852370001093436L;

	public final static int FIELD = 6000;

	public CustomOrderTypeField() {
		super(FIELD);
	}

	public CustomOrderTypeField(final String data) {
		super(FIELD, data);
	}

}
