
public class HMAStylePair {
	
	private String mKey;
	private boolean mDisplayFlag = false;
	
	HMAStylePair (String pKey, String pFlag) {
		this.mKey = pKey;
		if (pFlag.equalsIgnoreCase("none")) {
			this.mDisplayFlag = false;
		} else {
			this.mDisplayFlag = true;
		}
	}

	public String getKey() {
		return mKey;
	}

	public boolean isDisplayFlag() {
		return mDisplayFlag;
	}

	public void setKey(String pKey) {
		mKey = pKey;
	}

	public void setDisplayFlag(boolean pDisplayFlag) {
		mDisplayFlag = pDisplayFlag;
	}
}
