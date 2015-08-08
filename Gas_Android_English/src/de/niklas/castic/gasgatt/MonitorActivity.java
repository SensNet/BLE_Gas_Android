package de.niklas.castic.gasgatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import de.niklas.castic.gasgatt.R;

public class MonitorActivity extends Activity implements GASDataListener {
	private final static String TAG = MonitorActivity.class.getSimpleName();

	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private TextView mConnectionState;
	private TextView mDataField, mBatField;
	private String mDeviceName;
	private String mDeviceAddress;
	private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;

	private XYPlot plot;

	// Code to manage Service lifecycle.
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			mBluetoothLeService.connect(mDeviceAddress);
			mBluetoothLeService.registerGASDataListener(MonitorActivity.this);
			LineAndPointFormatter series1Format = new LineAndPointFormatter();
			series1Format.setPointLabelFormatter(new PointLabelFormatter());
			series1Format.configure(getApplicationContext(),
					R.xml.linepointformat);
			LineAndPointFormatter borderFormat = new LineAndPointFormatter();
			borderFormat.setPointLabelFormatter(new PointLabelFormatter());
			borderFormat
					.configure(getApplicationContext(), R.xml.borderformat);
			plot.addSeries(gasPlotSeries, series1Format);
			plot.addSeries(lowerSeries, borderFormat);
			plot.addSeries(upperSeries, borderFormat);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	private DynamicSeries gasPlotSeries;
	private StaticLineSeries upperSeries, lowerSeries;

	@Override
	protected void onResume() {
		super.onResume();
		if (mBluetoothLeService != null) {
			final boolean result = mBluetoothLeService.connect(mDeviceAddress);
			Log.d(TAG, "Connect request result=" + result);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gatt_services, menu);
		if (mConnected) {
			menu.findItem(R.id.menu_connect).setVisible(false);
			menu.findItem(R.id.menu_disconnect).setVisible(true);
		} else {
			menu.findItem(R.id.menu_connect).setVisible(true);
			menu.findItem(R.id.menu_disconnect).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_connect:
			mBluetoothLeService.connect(mDeviceAddress);
			return true;
		case R.id.menu_disconnect:
			mBluetoothLeService.disconnect();
			finish();
			return true;
		case R.id.menu_set_trigger:
			AlertDialog.Builder alert = new AlertDialog.Builder(this);

			alert.setTitle(R.string.triggerschwelle_slider);
			//alert.setMessage(R.string.untere_triggerschwelle);

			//final EditText input = new EditText(this);
			//alert.setView(input);
			LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_root_element, (ViewGroup)findViewById(R.id.your_dialog_root_element));
			alert.setView(layout);
			
			final TextView textViewUpper = (TextView)layout.findViewById(R.id.textViewUpper);
			final TextView textViewLower = (TextView)layout.findViewById(R.id.textViewLower);
			SeekBar lower_seekbar = (SeekBar)layout.findViewById(R.id.lower_seekbar);
			SeekBar upper_seekbar = (SeekBar)layout.findViewById(R.id.upper_seekbar);
			
			final int[] upperValue = {0};
			final int[] lowerValue = {0};
			
			upperValue[0] = BluetoothLeService.upperTrigger;
			lowerValue[0] = BluetoothLeService.lowerTrigger;
			
			textViewUpper.setText("Upper threshold: " + upperValue[0]);
			textViewLower.setText("Lower threshold: " + lowerValue[0]);
			
			lower_seekbar.setMax(150);
			lower_seekbar.setProgress(lowerValue[0] * -1);
			
			upper_seekbar.setMax(150);
			upper_seekbar.setProgress(upperValue[0]);
			
			OnSeekBarChangeListener upperSeekBarListener = new OnSeekBarChangeListener() {
			    @Override
			    public void onStopTrackingTouch(SeekBar seekBar) {
			    	int seekValue = seekBar.getProgress();
			    	upperValue[0] = seekValue;
			    	textViewUpper.setText("Upper threshold: " + Integer.toString(upperValue[0]));
			    }

			    @Override
			    public void onStartTrackingTouch(SeekBar seekBar) {
			            //add code here
			    }

			    @Override
			    public void onProgressChanged(SeekBar seekBark, int progress, boolean fromUser) {
			            //add code here
			    }
			 };
			 upper_seekbar.setOnSeekBarChangeListener(upperSeekBarListener);
			 
			 OnSeekBarChangeListener lowerSeekBarListener = new OnSeekBarChangeListener() {
				    @Override
				    public void onStopTrackingTouch(SeekBar seekBar) {
				    	int seekValue = seekBar.getProgress();
				    	lowerValue[0] = seekValue * -1;
				    	textViewLower.setText("Lower threshold: " + Integer.toString(lowerValue[0]));
				    }

				    @Override
				    public void onStartTrackingTouch(SeekBar seekBar) {
				            //add code here
				    }

				    @Override
				    public void onProgressChanged(SeekBar seekBark, int progress, boolean fromUser) {
				            //add code here
				    }
				 };
			lower_seekbar.setOnSeekBarChangeListener(lowerSeekBarListener);
				 
				 
			alert.setPositiveButton("Ok",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							
							mBluetoothLeService.sendUpper(upperValue[0]);
							mBluetoothLeService.sendLower(lowerValue[0]);
						}
					});

			alert.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog,
								int whichButton) {
							// Nothing.
						}
					});

			alert.show();
			break;
		
		case android.R.id.home:
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.data_display);

		final Intent intent = getIntent();
		mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
		mConnectionState = (TextView) findViewById(R.id.connection_state);
		mDataField = (TextView) findViewById(R.id.data_value);
		mBatField = (TextView) findViewById(R.id.data_battery);
		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		plot = (XYPlot) findViewById(R.id.degreedisp);
		gasPlotSeries = new DynamicSeries("Datas");
		plot.setRangeBoundaries(-180, 180, BoundaryMode.FIXED);
		plot.setTicksPerRangeLabel(3);
		plot.getGraphWidget().setDomainLabelOrientation(-45);
		upperSeries = new StaticLineSeries("Upper", 0, 40) {

			@Override
			public Number getX(int arg0) {
				return baseIndex + arg0;
			}

		};
		plot.getLayoutManager().remove(plot.getDomainLabelWidget());
		plot.getLayoutManager().remove(plot.getLegendWidget());
		lowerSeries = new StaticLineSeries("Lower", 0, 40) {

			@Override
			public Number getX(int arg0) {
				return baseIndex + arg0;
			}

		};

	}

	@Override
	public void onReceive(final float val, final boolean del) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				gasPlotSeries.addBaseIndex(1);
				plot.redraw();
			}
		});
	}

	@Override
	public void onDisconnected() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mConnected = false;
				updateConnectionState(R.string.disconnected);
				invalidateOptionsMenu();
				mDataField.setText(R.string.no_data);
			}
		});
	}

	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mConnectionState.setText(resourceId);
			}
		});
	}

	@Override
	public void onConnected() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mConnected = true;
				updateConnectionState(R.string.connected);
				invalidateOptionsMenu();
			}
		});
	}

	private abstract class StaticLineSeries implements XYSeries {
		private int size;
		private float value;
		private String title;

		public StaticLineSeries(String title, float value, int size) {
			this.value = value;
			this.title = title;
			this.size = size;
		}

		public void setValue(float value) {
			this.value = value;
		}

		@Override
		public String getTitle() {
			return title;
		}

		@Override
		public Number getY(int arg0) {
			return value;
		}

		@Override
		public int size() {
			return size;
		}

	}

	private int baseIndex = 0;
	private class DynamicSeries implements XYSeries {
		private String title;

		public DynamicSeries(String title) {
			this.title = title;
		}

		@Override
		public String getTitle() {
			return title;
		}

		@Override
		public int size() {
			return mBluetoothLeService.getDatas().size();
		}

		@Override
		public Number getX(int index) {
			return baseIndex + index;
		}

		@Override
		public Number getY(int index) {
			return mBluetoothLeService.getDatas().get(index);
		}

		public void addBaseIndex(int baseIndex) {
			MonitorActivity.this.baseIndex += baseIndex;
		}
	}

	@Override
	public void onCriticalValue() {
		// TODO Comming soon ;)
	}

	@Override
	public void onBattery(final float value) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mBatField.setText(value + "%");
			}
		});
	}

	@Override
	public void onTemperature(final float value) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				mDataField.setText(value + "°C");
			}
		});
	}

	@Override
	public void onBorderUpdate(final float lower, final float upper) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				MonitorActivity.this.lowerSeries.setValue(lower);
				MonitorActivity.this.upperSeries.setValue(upper);
			}
		});
	}

}
