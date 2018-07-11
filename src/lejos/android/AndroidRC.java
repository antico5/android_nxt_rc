package lejos.android;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import lejos.android.LeJOSDroid.CONN_TYPE;
import lejos.nxt.Motor;
import lejos.nxt.remote.NXTCommand;
import lejos.pc.comm.NXTConnector;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.TextView;

public class AndroidRC extends Activity implements SensorEventListener {
	protected static final String TAG = "AndroidRC";
	//Banderas de estado del programa.
	private boolean connected = false;
	private boolean connecting = false;
	private boolean wasConnected = false;
	private boolean sensorModeEnabled = false;
	
	//Objeto para manejar la conexion via bluetooth
	private NXTConnector conn;
	
	//Objeto necesario para poder usar los sensores.
	SensorManager sensorManager = null;
	
	//Objetos de la interfaz grafica
	TextView textS1;
	TextView textS2;
	TextView textS3;
	TextView mMessage;
	TextView mEstado;
	
	//variables para controlar el movimiento libre (modo teclas, no sensor)
	private long tiempo_pulso;
	private boolean mov_libre = false;
	
	//velocidades iniciales de los motores.
	int vel_A;
	int vel_C;
	
	//Variables auxiliares para controlar el movimiento con sensores.
	//Se usan para no mandar ordenes stop() y forward() todo el tiempo, solo cuando
	//sus velocidades deben cambiar.
	int auxPilotoSensor;
	int auxPilotoSensor2;
	int oldPilotoSensor = -1;
	
	//Se ejecuta cuando se crea la actividad
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG,"Iniciando onCreate de androidRC");
		setContentView(R.layout.androidrc);
		mMessage = (TextView) findViewById(R.id.androidrc_status);
		mEstado = (TextView) findViewById(R.id.androidrc_estado);
		textS1 = (TextView) findViewById(R.id.textS1);
		textS2 = (TextView) findViewById(R.id.textS2);
		textS3 = (TextView) findViewById(R.id.textS3);
		connected = false;
		setupBotones();
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		Log.d(TAG,"Terminado oncreate con exito");
	}

	//Se ejecuta cada vez que se pausa la actividad, ya sea enfocando otra actividad,
	//saliendo temporalmente de la aplicacion, etc.
	@Override
	protected void onPause() {
		Log.d(TAG,"ejecutando onPause");
		if(connected)
			wasConnected = true;
		else
			wasConnected = false;
		handleDesconectar(null);
	    sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION));
		super.onPause();
	}
	
	//Se ejecuta cuando se vuelve a enfocar la actividad (tambien una vez al iniciarla)
	@Override
	protected void onResume() {
		Log.d(TAG,"ejecutando onResume");
		super.onResume();
		if(wasConnected)
			handleConectar(null);
	    sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_GAME);
	}
	
	//Metodo para setear alguna caja de texto desde otro thread que no sea el principal
	//Es requerido por android.
	public void setTextOnUIThread(final TextView view,final String message) {
		Log.d(TAG,"ejecutando setText");
		runOnUiThread(new Runnable() {
	        public void run() {
	        	view.setText(message);
	        }
	      });
	}
	
	//Define la funcionalidad de cada boton y las condiciones para que se active (touch down, up, etc)
	private void setupBotones(){
		Log.d(TAG,"iniciando setupbotones");
		OnTouchListener listenerAdelante = new OnTouchListener(){
		    public boolean onTouch(View v, MotionEvent event) {
		    	Log.d(TAG,"onTouch en ADELANTE");
		    	if(!connected){
					setTextOnUIThread(mMessage,"No estas conectado!");
					return false;
				}
		        int action = event.getAction();
		        switch (action){
		        case MotionEvent.ACTION_DOWN:
		        	setTextOnUIThread(mMessage,"Adelante!");
		    		Motor.A.forward();
		    		Motor.C.forward();
		    		mov_libre = false;
		    		tiempo_pulso = SystemClock.elapsedRealtime();
		            break;

		        case MotionEvent.ACTION_UP:
		        	if(SystemClock.elapsedRealtime()-tiempo_pulso < 200){
		        		mov_libre = true;
		        		return false;
		        	}
		            Motor.A.stop();
		            Motor.C.stop();
		            break;
		        }
		        return false;
		    }

		};
		OnTouchListener listenerAtras = new OnTouchListener(){
		    public boolean onTouch(View v, MotionEvent event) {
		    	Log.d(TAG,"onTouch en Atras");
		    	if(!connected){
		    		setTextOnUIThread(mMessage,"No estas conectado!");
					return false;
				}
		        int action = event.getAction();
		        switch (action){
		        case MotionEvent.ACTION_DOWN:
		        	setTextOnUIThread(mMessage,"Atras!");
		    		Motor.A.backward();
		    		Motor.C.backward();
		    		mov_libre = false;
		            break;

		        case MotionEvent.ACTION_UP:
		            Motor.A.stop();
		            Motor.C.stop();
		            break;
		        }
		        return false;
		    }

		};
		OnTouchListener listenerDerecha = new OnTouchListener(){
		    public boolean onTouch(View v, MotionEvent event) {
		    	Log.d(TAG,"onTouch en DERECHA");
		    	if(!connected){
		    		setTextOnUIThread(mMessage,"No estas conectado!");
					return false;
				}
		        int action = event.getAction();
		        switch (action){
		        case MotionEvent.ACTION_DOWN:
		        	setTextOnUIThread(mMessage,"Derecha!");
		        	if(mov_libre == false){
			    		Motor.A.backward();
			    		Motor.C.forward();
			    		break;
		        	}
		        	Motor.A.stop();
		        	Motor.A.setSpeed(vel_A/2);
		        	Motor.A.forward();
		            break;

		        case MotionEvent.ACTION_UP:
		        	if(mov_libre == false){
			            Motor.A.stop();
			            Motor.C.stop();
			            break;
		        	}
		        	Motor.A.stop();
		        	Motor.A.setSpeed(vel_A);
		        	Motor.A.forward();
		            break;
		        }
		        return false;
		    }

		};
		OnTouchListener listenerIzquierda = new OnTouchListener(){
		    public boolean onTouch(View v, MotionEvent event) {
		    	Log.d(TAG,"onTouch en IZQUIERDA");
		    	if(!connected){
		    		setTextOnUIThread(mMessage,"No estas conectado!");
					return false;
				}
		        int action = event.getAction();
		        switch (action){
		        case MotionEvent.ACTION_DOWN:
		        	setTextOnUIThread(mMessage,"Izquierda!");
		        	if(mov_libre == false){
			    		Motor.C.backward();
			    		Motor.A.forward();
			    		break;
		        	}
		        	Motor.C.stop();
		        	Motor.C.setSpeed(vel_C/2);
		        	Motor.C.forward();
		            break;

		        case MotionEvent.ACTION_UP:
		        	if(mov_libre == false){
			            Motor.C.stop();
			            Motor.A.stop();
			            break;
		        	}
		        	Motor.C.stop();
		        	Motor.C.setSpeed(vel_C);
		        	Motor.C.forward();
		            break;
		        }
		        return false;
		    }

		};
		OnCheckedChangeListener listenerSensorMode = new OnCheckedChangeListener(){
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
		    {
		        if (isChecked){
		            sensorModeEnabled = true;
		        }
		        else {
		        	sensorModeEnabled = false;
		        	if(connected){
		        		//Volver a las velocidades originales por si se usa el control con teclas.
		        		Motor.A.setSpeed(vel_A);
		        		Motor.C.setSpeed(vel_C);
		        	}
		        }

		    }
		};
		
		Log.d(TAG,"medio de setupbotones");
		ImageButton botonAdelante = (ImageButton) findViewById(R.id.botonAdelante);
		botonAdelante.setOnTouchListener(listenerAdelante);
		
		ImageButton botonAtras = (ImageButton) findViewById(R.id.botonAtras);
		botonAtras.setOnTouchListener(listenerAtras);
		
		ImageButton botonDerecha = (ImageButton) findViewById(R.id.botonDerecha);
		botonDerecha.setOnTouchListener(listenerDerecha);
		
		ImageButton botonIzquierda = (ImageButton) findViewById(R.id.botonIzquierda);
		botonIzquierda.setOnTouchListener(listenerIzquierda);
		
		CheckBox optSensor = (CheckBox)findViewById(R.id.optSensor);
		optSensor.setOnCheckedChangeListener(listenerSensorMode);
		Log.d(TAG,"fin setupbotones");
	}
	
	//maneja los comandos de voz
	public void handleVoz(View v){
		Intent i = new Intent(this,lejos.android.VoiceHandler.class);
		this.startActivity(i);
	}
	
	//Metodo activado cuando se pulsa el boton conectar
	//Chequea las condiciones necesarias para establecer una conexion, y la realiza.
	public void handleConectar(View v) {
		if(connected){
			setTextOnUIThread(mMessage,"Ya estas conectado");
			return;
		}
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null){
			setTextOnUIThread(mMessage,("Tu dispositivo no soporta bluetooth"));
			return;
		}
		if(!mBluetoothAdapter.isEnabled()){
			setTextOnUIThread(mMessage,("Debes activar el bluetooth"));
			return;
		}
		connecting = true;
		
		//Animacion de connecting
		new Thread(new Runnable() {
		    public void run() {
		    	String puntos[] = {"",".","..","...","....",".....","....","...","..","."};
		    	int i = 0;
		    	while(connecting){
		    		setTextOnUIThread(mMessage,puntos[i%10]+"Conectando"+puntos[i%10]);
		    		i++;
		    		try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		    	}
		    }
		}).start();    
		
		//Thread conectar
		new Thread(new Runnable() {
		    public void run() {
		    	try{
		    		Looper.prepare();
		    		conn = LeJOSDroid.connect(CONN_TYPE.LEGO_LCP);
		    		if(conn != null){
		    			connected = true;
		    			NXTCommand.getSingleton().setNXTComm(conn.getNXTComm());
		    			setTextOnUIThread(mMessage,"Conexion Satisfactoria");
			    	    setTextOnUIThread(mEstado,"CONECTADO");
			    	    vel_A = Motor.A.getSpeed();
			    	    vel_C = Motor.C.getSpeed();
			    	    //Motor.A.setSpeed(vel_A);
			    	    //Motor.C.setSpeed(vel_C);
			    	    
		    		}
		    		else{
		    			connected = false;
			    	    setTextOnUIThread(mMessage,"No se encontro ningun NXT.");
			    	    setTextOnUIThread(mEstado,"DESCONECTADO");
		    		}
		    		connecting = false;
		    		Looper.loop();
		    		Looper.myLooper().quit();
		    	}
		    	catch(final Exception e){
		    		runOnUiThread(new Runnable() {
		    	        public void run() {
		    	        	setContentView(R.layout.error);
		    	        	TextView tv = (TextView)findViewById(R.id.errorText);
		    	        	tv.setText(stack2string(e));
		    	        }
		    	      });
		    	}
		    }
		  }).start();

	}
	
	//Metodo activado cuando se pulsa el boton Stop
	public void handleStop(View v) {
		Log.d(TAG,"Boton presionado");
		if(!connected){
			setTextOnUIThread(mMessage,"No estas conectado!");
			return;
		}
		setTextOnUIThread(mMessage,"Stop!");
		Motor.A.stop();
		Motor.C.stop();
		mov_libre = false;
	}
	
	//Metodo activado cuando se pulsa el boton Desconectar
	public void handleDesconectar(View v) {
		Log.d(TAG,"Desconectando...");
		
		if(!connected){
			setTextOnUIThread(mMessage,"Ya estas desconectado.");
			return;
		}
		
		setTextOnUIThread(mMessage,"Desconectar!");
		connected = false;
		setTextOnUIThread(mEstado,"DESCONECTADO");
		if (conn != null) {
			try {
				Motor.A.setSpeed(vel_A);
				Motor.C.setSpeed(vel_C);
				Motor.A.stop();
				Motor.C.stop();
				conn.getNXTComm().close();
				conn.close();
				conn = null;
			} catch (IOException e) {
				Log.e(TAG, "Error closing connection", e);
			}
		}
	}
	
	//Dada una excepcion devuelve en una string los detalles de la misma.
	public static String stack2string(Exception e) {
		   try {
		     StringWriter sw = new StringWriter();
		     PrintWriter pw = new PrintWriter(sw);
		     e.printStackTrace(pw);
		     return "------\r\n" + sw.toString() + "------\r\n";
		   }
		   catch(Exception e2) {
		     return "bad stack2string";
		   }
	}
	
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	//Metodo activado cuando cambia un sensor. Encargado de responder a estos cambios.
	public void onSensorChanged(SensorEvent event) {
		synchronized (this) {
			//Para procesar los valores del sensor se debe estar conectado al robot
			//y ademas estar habilitado el movimiento con sensor.
			if(!isSensorModeEnabled() || !connected) 
				return;
			
	        switch (event.sensor.getType()){
	        case Sensor.TYPE_ORIENTATION:
	                textS1.setText("V1: "+Integer.toString((int)event.values[0]));
	                textS2.setText("V2 :"+Integer.toString((int)event.values[1]));
	                textS3.setText("V3 :"+Integer.toString((int)event.values[2]));
	                sensorControl((int)event.values[1],(int)event.values[2]);
	        break;
	        }
	    }
	}
	public boolean isSensorModeEnabled(){
		return sensorModeEnabled;
	}
	
	//Metodo encargado de procesar los cambios dados en el sensor de orientacion.
	//Lo llama onSensorChanged con el 2do y 3er valor del sensor (giros alrededor del eje Y y X)
	private void sensorControl(int s2, int s3){
		
		//Cada multiplicador es el porcentaje de la velocidad maxima de los motores.
		float rightMult = 0,leftMult = 0;
		
		//En estos 2 grupos de condiciones se determina el comportamiento que van a tener los motores
		//en funcion de los valores del sensor. Esta basado en la experiencia con el mismo.
		if(Math.abs(s2) > 60){
			// 0 -> ambos motores frenados.
			rightMult = 0;
			leftMult = 0;
			auxPilotoSensor= 1;
		}
		else if(Math.abs(s2)<= 60 && Math.abs(s2)>30){
			//50% de V max
			rightMult = 0.5f;
			leftMult = 0.5f;
			auxPilotoSensor = 2;
		}
		else {
			// V max
			rightMult = 1.0f;
			leftMult = 1.0f;
			auxPilotoSensor = 4;
		}
		
		if(s3 >= -30 && s3 <30){
			//no pasa nada
			auxPilotoSensor2 = 8;
		}
		else if(s3 >= -60 && s3 <-30){
			//Ej: motor derecho frenado
			rightMult = 0f;
			leftMult = 1.0f;
			auxPilotoSensor2 = 16;
		}
		else if(s3 < -60){
			//Ej: motores en movimiento opuesto. Gira en el lugar en sentido
			//de las agujas del reloj.
			rightMult = -1.0f;
			leftMult = 1.0f;
			auxPilotoSensor2 = 32;
		}
		else if(s3 < 60 && s3 >= 30){
			rightMult = 1f;
			leftMult = 0f;
			auxPilotoSensor2 = 64;
		}
		else if(s3 >= 60){
			rightMult = 1.0f;
			leftMult = -1.0f;
			auxPilotoSensor2 = 128;
		}
		
		//Los valores auxiliares sirven para indicar la configuracion actual del movimiento.
		//Si el movimiento no cambio desde el ultimo ciclo, no envia las ordenes de
		//stop,setspeed,forward, etc.
		if ((auxPilotoSensor + auxPilotoSensor2) != oldPilotoSensor){
			oldPilotoSensor = auxPilotoSensor + auxPilotoSensor2;
			Motor.A.stop();
			Motor.A.setSpeed((int)Math.abs( vel_A*rightMult));
			if(rightMult >0){
				Motor.A.forward();
			}
			else{
				Motor.A.backward();
			}
			
			Motor.C.stop();
			Motor.C.setSpeed((int)Math.abs( vel_C*leftMult));
			if(leftMult >0){
				Motor.C.forward();
			}
			else{
				Motor.C.backward();
			}
		}
	}
}
