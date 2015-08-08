package de.niklas.castic.gasgatt;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import de.niklas.castic.gasgatt.R;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;

public class BluetoothLeService extends Service {
	private static final UUID UUID_GAS = UUID
			.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

	private final static String TAG = BluetoothLeService.class.getSimpleName();

	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;
	private int connectionState = STATE_DISCONNECTED;
	private String prevRecCache = null;
	private static final int STATE_DISCONNECTED = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;
	private LinkedList<Float> datas = new LinkedList<Float>();
	public final static String ACTION_GATT_CONNECTED = "de.niklas.castic.gasgatt.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "de.niklas.castic.gasgatt.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "de.niklas.castic.gasgatt.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_AVAILABLE = "de.niklas.castic.gasgatt.ACTION_DATA_AVAILABLE";
	public final static String EXTRA_DATA = "de.niklas.castic.gasgatt.EXTRA_DATA";
	public final static UUID UUID_DATA = UUID
			.fromString("00002a37-0000-1000-8000-00805f9b34fb");

	@Override
	public void onCreate() {
		startForeground(-100, new Notification());
		notifer = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		super.onCreate();
	}

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				connectionState = STATE_CONNECTED;
				if (currentData != null) {
					currentData.onConnected();
				}
				Log.i(TAG, "Connected to GATT server.");
				Log.i(TAG, "Attempting to start service discovery:"
						+ mBluetoothGatt.discoverServices());

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				connectionState = STATE_DISCONNECTED;
				Log.i(TAG, "Disconnected from GATT server.");
				if (currentData != null) {
					currentData.onDisconnected();
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				readCharacteristic();
				// broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w(TAG, "onServicesDiscovered received: " + status);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(characteristic);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			if (wantUpdate) {
				byte[] tmp = ourCharacteristic.getValue();
				ourCharacteristic.setValue("$" + lowerTrigger + ";"
						+ upperTrigger + "\n");
				boolean res = mBluetoothGatt
						.writeCharacteristic(ourCharacteristic);
				Log.d("SERVICE",
						"wrote:" + new String(ourCharacteristic.getValue())
								+ "(" + res + ")");
				if (res) {
					wantUpdate = false;
				}
				ourCharacteristic.setValue(tmp);
			}
			broadcastUpdate(characteristic);
		}
	};

	private void broadcastUpdate(
			final BluetoothGattCharacteristic characteristic) {
			final byte[] data = characteristic.getValue();
			if (data != null && data.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(
						data.length);
				for (byte byteChar : data) {
					stringBuilder.append(String.format("%02X ", byteChar));
				}
				try {
					// 100 100 10
					Log.d("DATA", stringBuilder.toString());
					String rawData = new String(data);
					if (!rawData.endsWith("\n")) {
						if (prevRecCache == null) {
							prevRecCache = "";
						}
						prevRecCache += rawData;
						return;
					} else if (prevRecCache != null) {
						prevRecCache += rawData;
						rawData = prevRecCache;
						rawData = rawData.trim();
						prevRecCache = null;
					}
					if (rawData.startsWith("$")) {
						rawData = rawData.substring(1);
						String[] dt = rawData.split(";");
						upperTrigger = Integer.parseInt(dt[1]);
						lowerTrigger = Integer.parseInt(dt[0]);
						if (currentData != null) {
							currentData.onBorderUpdate(Integer.parseInt(dt[0]),
									Integer.parseInt(dt[1]));
						}
					} else {
						String[] datas = rawData.split(";");
						float degValue = Float.parseFloat(datas[0]);
						BluetoothLeService.this.datas.add(degValue / 100f);
						boolean del = false;
						if (BluetoothLeService.this.datas.size() >= 40) {
							for (int i = 0; i < 1; i++) {
								BluetoothLeService.this.datas.remove(i);
								del = true;
							}
						}
						Log.d("DATA", datas[0]);
						if (currentData != null) {
							currentData.onReceive(degValue, del);
							currentData.onTemperature(Float
									.parseFloat(datas[1]) / 100f);
							currentData
									.onBattery(Float.parseFloat(datas[2]) / 10f);
						}
						if (degValue / 100f > upperTrigger
								|| degValue / 100f < lowerTrigger) {
							currentData.onCriticalValue();

							Notification.Builder b = new Builder(
									getApplicationContext());
							b.setAutoCancel(false);
							b.setContentTitle(getString(R.string.kritische_gaskonzentartion_)
									+ mBluetoothDeviceAddress + ")");
							b.setOnlyAlertOnce(false);
							b.setContentText("Value at " + degValue / 100f
									+ "°");
							b.setSmallIcon(android.R.drawable.ic_dialog_alert);
							vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
							vib.vibrate(new long[] { 1000, 1000 }, 1);
							b.setLights(0xFFFF0000, 500, 100);
							playSound(getApplicationContext());
							notifer.notify(200, b.build());
						} else {
							notifer.cancel(200);
							vib.cancel();
							if (soundAlarmActive) {
								alarmSoundPlayer.stop();
								soundAlarmActive = false;
							}
						}
						if (Float.parseFloat(datas[2]) / 10f <= 5) {
							Notification.Builder b = new Builder(
									getApplicationContext());
							b.setAutoCancel(false);
							b.setContentTitle("Low battery! ("
									+ mBluetoothDeviceAddress + ")");
							b.setOnlyAlertOnce(false);
							b.setContentText("Battery at "
									+ Float.parseFloat(datas[2]) / 10f + "%");
							b.setSmallIcon(android.R.drawable.ic_dialog_alert);
							vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
							vib.vibrate(new long[] { 1000, 1000 }, 1);
							b.setLights(0xFFFF0000, 500, 100);
							notifer.notify(100, b.build());
						} else {
							notifer.cancel(100);
						}
					}
				} catch (Throwable e) {
					Log.w("LE SERVICE", e);
				}
			}
		
	}

	public class LocalBinder extends Binder {
		BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	public boolean initialize() {
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	public boolean connect(final String address) {
		if (mBluetoothAdapter == null || address == null) {
			Log.w(TAG,
					"BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		if (mBluetoothDeviceAddress != null
				&& address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {
			Log.d(TAG,
					"Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				connectionState = STATE_CONNECTING;
				return true;
			} else {
				return false;
			}
		}

		final BluetoothDevice device = mBluetoothAdapter
				.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		Log.d(TAG, "Trying to create a new connection.");
		mBluetoothDeviceAddress = address;
		connectionState = STATE_CONNECTING;
		return true;
	}

	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		vib.cancel();
		if (soundAlarmActive) {
			alarmSoundPlayer.stop();
		}
		mBluetoothGatt.disconnect();
		stopSelf();
	}

	public void close() {
		if (mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	private BluetoothGattCharacteristic ourCharacteristic;

	public void readCharacteristic() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		List<BluetoothGattService> list = getSupportedGattServices();
		for (BluetoothGattService bluetoothGattService : list) {
			if (bluetoothGattService.getUuid().equals(
					UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))) {
				ourCharacteristic = bluetoothGattService
						.getCharacteristic(UUID_GAS);
			}
		}
		mBluetoothGatt.readCharacteristic(ourCharacteristic);
		mBluetoothGatt.setCharacteristicNotification(ourCharacteristic, true);
	}

	// graph: farbe mit triggerschwelle phase/temp/akku
	public void setCharacteristicNotification(
			BluetoothGattCharacteristic characteristic, boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		if (UUID_DATA.equals(characteristic.getUuid())) {
			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(UUID_DATA);
			descriptor
					.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
	}

	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBluetoothGatt == null) {
			return null;
		}

		return mBluetoothGatt.getServices();
	}

	private GASDataListener currentData;

	private NotificationManager notifer;

	public void registerGASDataListener(GASDataListener serviceConnection) {
		currentData = serviceConnection;
	}

	public int getConnectionState() {
		return connectionState;
	}

	public LinkedList<Float> getDatas() {
		return datas;
	}

	static int lowerTrigger, upperTrigger;
	private boolean wantUpdate = false;

	private Vibrator vib;

	private MediaPlayer alarmSoundPlayer;

	private void updateTrigger() {
		wantUpdate = true;
		currentData.onBorderUpdate(lowerTrigger, upperTrigger);
	}

	public void sendLower(int value) {
		lowerTrigger = value;
		Log.d("SERVICE", "Send lower: " + value);
		updateTrigger();
	}

	public void sendUpper(int value) {
		Log.d("SERVICE", "Send upper: " + value);
		upperTrigger = value;
		updateTrigger();
	}

	private boolean soundAlarmActive = false;

	public void playSound(Context context) throws IllegalArgumentException,
			SecurityException, IllegalStateException, IOException {
		if (soundAlarmActive) {
			return;
		}
		soundAlarmActive = true;
		Uri soundUri = RingtoneManager
				.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		alarmSoundPlayer = new MediaPlayer();
		alarmSoundPlayer.setDataSource(context, soundUri);
		final AudioManager audioManager = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);

		if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
			alarmSoundPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
			alarmSoundPlayer.setLooping(true);
			alarmSoundPlayer.prepare();
			alarmSoundPlayer.start();
		}
	}
}
