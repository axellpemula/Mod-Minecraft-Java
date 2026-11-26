package com.wheelofadaptation.adaptation;

/**
 * Mode Wheel (dari Bedrock: Attack / Balance / Defense).
 *
 * - ATTACK: bonus damage sword/slash, adaptasi serangan lebih cepat
 * - BALANCE: seimbang (default Bedrock)
 * - DEFENSE: nullify lebih kuat, heal lebih besar, damage sword berkurang
 */
public enum WheelMode {
	ATTACK("Attack"),
	BALANCE("Balance"),
	DEFENSE("Defense");

	private final String displayName;

	WheelMode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public WheelMode next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
