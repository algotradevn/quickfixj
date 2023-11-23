package quickfix.custom;

import quickfix.IntField;

public class BuyValidOrderCnt extends IntField {

	private static final long serialVersionUID = 538115767857327489L;

	public final static int FIELD = 30523;

	public BuyValidOrderCnt() {
		super(FIELD);
	}

	public BuyValidOrderCnt(final Integer data) {
		super(FIELD, data);
	}

}
