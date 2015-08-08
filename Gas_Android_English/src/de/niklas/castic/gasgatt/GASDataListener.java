package de.niklas.castic.gasgatt;

public interface GASDataListener {
	public void onReceive(float val, boolean del);

	public void onBattery(float value);

	public void onTemperature(float value);

	public void onDisconnected();

	public void onConnected();

	public void onCriticalValue();

	public void onBorderUpdate(float lower, float upper);
}
