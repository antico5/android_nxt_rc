package lejos.android;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class VoiceHandler extends Activity {
	private static int RCODE = 327423;
	private Button bComando;
	private ListView wordsList;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.voiceview);
		bComando = (Button)findViewById(R.id.bComando);
		wordsList = (ListView)findViewById(R.id.lResultados);
		
		//chequear que haya un reconocedor de voz disponible
		PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(
                new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (activities.size() == 0)
        {
            bComando.setEnabled(false);
            bComando.setText("No hay reconocedor de voz");
        }
		
		
	}
	
	@Override
	protected void onPause() {
		super.onPause();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
	}
	
	/**
	 * boton de comando seleccionado
	 */
	public void handleComando(View boton){
		empezarReconocedorDeVoz();
	}

	private void empezarReconocedorDeVoz() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga el comando..");
        startActivityForResult(intent, RCODE);
		
	}
	
	@Override
	/**
	 * el reconocedor nos devuelve una respuesta
	 */
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == RCODE && resultCode == RESULT_OK)
        {
            // Populate the wordsList with the String values the recognition engine thought it heard
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            this.palabrasObtenidas(matches);
            //wordsList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,matches));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
	/**
	 * conjunto de palabras probables que el reconocedor obtuvo.
	 * @param matches las palabras que se obtuvieron
	 */
	private void palabrasObtenidas(ArrayList<String> matches) {
		//mostrar los resultados en la lista
		wordsList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,matches));
		
		//si el comando es salir, volver a la actividad anterior
		if(contieneComando(matches,"sali")){
			this.finish();
		}
	}
	
	private boolean contieneComando(List<String> lista, String comando){
		for(String s : lista){
			if (s.startsWith(comando)){
				return true;
			}
		}
		return false;
	}
	
}
