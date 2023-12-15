package quickfix.custom;

import quickfix.IntField;

public class MDEntryMMSize extends IntField {

	private static final long serialVersionUID = -7988703668168770477L;

	public final static int FIELD = 30271;

	public MDEntryMMSize() {
		super(FIELD);
	}

	public MDEntryMMSize(final Integer data) {
		super(FIELD, data);
	}

}
