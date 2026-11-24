package com.wiplay.wiplayandroid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Push por Firebase Cloud Messaging, sin OneSignal.
 *
 * El backend manda notification {title, body, image} + data {title, body, url, image}:
 * - en segundo plano la pinta el sistema y, al pulsarla, abre MainActivity con los data en los
 *   extras ("url");
 * - en primer plano llega a onMessageReceived y la notificacion se monta aqui, con el mismo
 *   "url" en el PendingIntent.
 */
public class PushService extends FirebaseMessagingService {

    static final String CANAL = "avisos";
    private static final String PREFS = "push";
    private static final String CLAVE_TOKEN = "token";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.i("Push", "Token nuevo de Firebase");
        MainActivity activa = MainActivity.activa;
        if (activa != null) {
            // La web se recarga con el token para que el servidor lo asocie al usuario
            activa.tokenRecibido(token);
        }
        // Si la app no esta abierta se manda en el proximo arranque: MainActivity pide el
        // token y, al ser distinto del guardado, recarga la web con el.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage mensaje) {
        Map<String, String> data = mensaje.getData();
        RemoteMessage.Notification n = mensaje.getNotification();
        String titulo = primero(data.get("title"), n != null ? n.getTitle() : null, getString(R.string.app_name));
        String texto = primero(data.get("body"), n != null ? n.getBody() : null, "");
        String imagen = primero(data.get("image"), n != null && n.getImageUrl() != null ? n.getImageUrl().toString() : null, null);

        Intent abrir = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        for (Map.Entry<String, String> e : data.entrySet()) {
            abrir.putExtra(e.getKey(), e.getValue());
        }
        int id = (int) System.currentTimeMillis();
        PendingIntent pulsar = PendingIntent.getActivity(this, id, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        crearCanal(this);
        NotificationCompat.Builder aviso = new NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setColor(Color.parseColor(getString(R.string.principal_color)))
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pulsar);

        Bitmap foto = descargar(imagen);
        if (foto != null) {
            aviso.setLargeIcon(foto)
                    .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(foto).setSummaryText(texto));
        }

        try {
            NotificationManagerCompat.from(this).notify(id, aviso.build());
        } catch (SecurityException e) {
            // Android 13+ sin el permiso de notificaciones concedido
            Log.w("Push", "Sin permiso para mostrar notificaciones", e);
        }
    }

    static void crearCanal(Context contexto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(CANAL,
                    contexto.getString(R.string.canal_notificaciones), NotificationManager.IMPORTANCE_HIGH);
            contexto.getSystemService(NotificationManager.class).createNotificationChannel(canal);
        }
    }

    static String tokenGuardado(Context contexto) {
        return contexto.getSharedPreferences(PREFS, MODE_PRIVATE).getString(CLAVE_TOKEN, null);
    }

    static void guardarToken(Context contexto, String token) {
        contexto.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CLAVE_TOKEN, token).apply();
    }

    // onMessageReceived corre en un hilo de fondo: se puede descargar la imagen aqui mismo
    private static Bitmap descargar(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            HttpURLConnection conexion = (HttpURLConnection) new URL(url).openConnection();
            conexion.setConnectTimeout(5000);
            conexion.setReadTimeout(5000);
            try (InputStream in = conexion.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conexion.disconnect();
            }
        } catch (Exception e) {
            Log.w("Push", "No se ha podido descargar la imagen del push", e);
            return null;
        }
    }

    private static String primero(String a, String b, String porDefecto) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return porDefecto;
    }
}
