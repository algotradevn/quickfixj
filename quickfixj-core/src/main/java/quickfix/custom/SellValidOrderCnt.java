package quickfix.custom;

import quickfix.IntField;

public class SellValidOrderCnt extends IntField {

	private static final long serialVersionUID = 538115767857327489L;

	public final static int FIELD = 30524;

	public SellValidOrderCnt() {
		super(FIELD);
	}

	public SellValidOrderCnt(final Integer data) {
		super(FIELD, data);
	}
}
