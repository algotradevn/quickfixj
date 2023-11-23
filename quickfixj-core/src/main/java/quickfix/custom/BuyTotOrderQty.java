package quickfix.custom;

import quickfix.IntField;

public class BuyTotOrderQty extends IntField {

	private static final long serialVersionUID = 538115767857327489L;

	public final static int FIELD = 30522;

	public BuyTotOrderQty() {
		super(FIELD);
	}

	public BuyTotOrderQty(final Integer data) {
		super(FIELD, data);
	}

}
