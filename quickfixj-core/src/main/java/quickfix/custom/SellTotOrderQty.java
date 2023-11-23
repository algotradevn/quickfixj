package quickfix.custom;

import quickfix.IntField;

public class SellTotOrderQty extends IntField {

	private static final long serialVersionUID = 538115767857327489L;

	public final static int FIELD = 30521;

	public SellTotOrderQty() {
		super(FIELD);
	}

	public SellTotOrderQty(final Integer data) {
		super(FIELD, data);
	}

}
