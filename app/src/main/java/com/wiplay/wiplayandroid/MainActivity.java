package com.wiplay.wiplayandroid;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.animation.ObjectAnimator;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.onesignal.OSDeviceState;
import com.onesignal.OSSubscriptionObserver;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;
import androidx.webkit.WebViewFeature;
import androidx.webkit.ProxyController;
import androidx.webkit.ProxyConfig;

public class MainActivity extends AppCompatActivity implements OSSubscriptionObserver {
    private WebView mWebView;
    private ImageView cargando;
    private ProgressBar progressBar;
    private View progressView;
    private Button backButton;

    private ValueCallback<Uri[]> mUploadMessage;
    private WebChromeClient.FileChooserParams mFileChooserParams;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private final static int FILECHOOSER_RESULTCODE=1;

    private String URL;
    private String ONESIGNAL_ID;

    private String DOMAIN;

    private FusedLocationProviderClient locationClient;

    private Boolean paginaCargada = false;

    private Location locationTemporal;

    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Handler handler = new Handler();
    private Runnable sendLocationRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d("Thread", "CCM - Sending: " + locationTemporal + " - " + paginaCargada);
            if (locationTemporal != null && paginaCargada) {
                sendLocationToWebView(locationTemporal.getLatitude(), locationTemporal.getLongitude());
                // Programa el siguiente envío en una hora
                handler.postDelayed(this, 1000 * 60 * 60);
            } else {
                //Si no hemos enviado, paramos durante 5 segundos solo
                handler.postDelayed(this, 1000 * 5);
            }
        }
    };

    @Override
    public void onBackPressed() {
        mWebView.evaluateJavascript("javascript:tabBack()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                System.out.println("onReceiveValue - " + value);
                if (value == null || value.equals("null")) {
                    // Si hubo un error o la función no existe, hacer goBack
                    if (mWebView.canGoBack()) {
                        mWebView.goBack();
                    } else {
                        //Evitar que se cierra la app al apretár atrás
                        //MainActivity.super.onBackPressed();
                    }
                }
            }
        });
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("onCreate", "CCM - Init");

        URL = getString(R.string.app_url);
        DOMAIN = getString(R.string.app_intent);
        ONESIGNAL_ID = getString(R.string.app_onesignal_id);

        setContentView(R.layout.activity_main);

        // Incorporamos Push con OneSignal
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE);
        OneSignal.initWithContext(this);
        OneSignal.setAppId(this.ONESIGNAL_ID);
        OneSignal.addSubscriptionObserver(this);

        mWebView = findViewById(R.id.webview);
        cargando = findViewById(R.id.logo);
        progressBar = findViewById(R.id.progressBar);
        progressView = findViewById(R.id.progressView);
        backButton = findViewById(R.id.backButton);
        System.out.println("CCMNEW - progressBar" + progressBar + " - " + backButton + " - " + mWebView);

        String principalColor = getResources().getString(R.string.principal_color);
        String secondaryColor = getResources().getString(R.string.secondary_color);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor(secondaryColor)));
        progressBar.setIndeterminate(true);
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.start();

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWebView.canGoBack()) {
                    mWebView.goBack();
                }
            }
        });

        // Permitir la descarga de documentos desde WEBVIEW
        mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Descarregant fitxer...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Descarregant fitxer", Toast.LENGTH_LONG).show();
                cargando.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                progressView.setVisibility(View.GONE);
            }
        });

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; VOG-L29) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.5359.79 Mobile Safari/537.36 OPR/63.3.3216.58675");
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // ✅ enables localStorage
        webSettings.setBuiltInZoomControls(true);
        webSettings.setTextZoom(90);
        webSettings.setAllowFileAccess(true);
        webSettings.setGeolocationEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        if(android.os.Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        if (BuildConfig.DEBUG && !BuildConfig.PROXY_LOCAL.isEmpty()
                && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            // Solo en debug con -PurlLocal: todo el trafico del WebView pasa por el proxy del
            // Mac para llegar a *.localhost. removeImplicitRules hace que localhost tambien
            // vaya por el proxy (por defecto Chromium nunca lo envia a un proxy).
            ProxyConfig proxyConfig = new ProxyConfig.Builder()
                    .addProxyRule(BuildConfig.PROXY_LOCAL)
                    .removeImplicitRules()
                    .build();
            ProxyController.getInstance().setProxyOverride(proxyConfig, Runnable::run, this::cargarInicio);
            Log.i("ProxyLocal", "WebView por el proxy " + BuildConfig.PROXY_LOCAL + " hacia " + URL);
        } else {
            cargarInicio();
        }

        /**
         * Configuración de la geolocalizacion
         */
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
        startLocationUpdates();
        handler.post(sendLocationRunnable);
        configLoader();

        /**
         * Configuración de las notificaciones
         */
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            // Las notificaciones no están habilitadas, abrir la configuración de la aplicación
            Intent intent2 = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent2.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent2.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                intent2.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent2.putExtra("app_package", getPackageName());
                intent2.putExtra("app_uid", getApplicationInfo().uid);
            } else {
                intent2.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent2.setData(Uri.fromParts("package", getPackageName(), null));
            }
            startActivity(intent2);
        }
    }

    private void cargarInicio() {
        Intent intent = getIntent();
        if (intent.getData() != null) {
            // Si hemos interceptado alguna petición
            mWebView.loadUrl(intent.getData().toString());
        } else {
            // Sino pintamos el acceso por defecto. Si ya tenemos suscripcion de OneSignal se manda
            // en cada arranque (no solo cuando cambia): asi el servidor la vuelve a asociar al
            // usuario con sesion iniciada aunque otro dispositivo la haya sustituido.
            System.out.println("Loading URL: " + this.URL);
            mWebView.loadUrl(this.URL + "?app=true" + parametrosSuscripcion());
        }
    }

    private String parametrosSuscripcion() {
        OSDeviceState estado = OneSignal.getDeviceState();
        String userID = estado != null ? estado.getUserId() : null;
        return userID == null ? "" : "&userID=" + userID + "&osApp=" + ONESIGNAL_ID;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Verifica si la solicitud de permisos fue concedida.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                System.out.println("LOCATION_PERMISSION_REQUEST_CODE - ACEPTADO");
                startLocationUpdates();
            } else {
                System.out.println("LOCATION_PERMISSION_REQUEST_CODE - DENEGADO");
            }
        }
    }

    private void startLocationUpdates() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000*60*60); // Intervalo de actualización deseado, e.g., 10 segundos
        locationRequest.setFastestInterval(10000); // La tasa más rápida en milisegundos en la que tu aplicación puede manejar actualizaciones
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //locationRequest.setSmallestDisplacement(100); // Distancia mínima entre actualizaciones en metros, e.g., 100 metros

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Si la página ha cargado y la ubicación ha cambiado, envía la nueva ubicación al WebView
                    if (paginaCargada) {
                        sendLocationToWebView(location.getLatitude(), location.getLongitude());
                    }
                    locationTemporal = location;
                }
            }

        };

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    locationTemporal = location;
                    if (paginaCargada) {
                        sendLocationToWebView(location.getLatitude(), location.getLongitude());
                    }
                }
            });

            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void stopLocationUpdates() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback);
    }

    private void sendLocationToWebView(double lat, double lon) {
        Log.d("web", "CCM - Send location: " + this.paginaCargada);
        final String jsCode = "javascript:updateLatLon(" + lat + "," + lon + ");";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mWebView.evaluateJavascript(jsCode, null);
            }
        });
    }

    private void configLoader() {
        Log.d("Config", "CCM - Config");
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.message().contains("Failed to execute 'postMessage'")) {
                    mWebView.loadUrl(URL + "/prMobileHome?tabAction=profile");
                } else {
                    //Log.d("MyApplication", consoleMessage.message() + " -- From line " + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                // Si quedaba una seleccion a medias hay que cerrarla: el WebView no vuelve a
                // abrir el selector mientras tenga un callback sin responder.
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;
                mFileChooserParams = fileChooserParams;
                openFileExplorer();
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                // Aquí otorgas el permiso para la geolocalización en el WebView
                Log.d("WebView", "CCM - onGeolocationPermissionsShowPrompt called for origin: " + origin);
                callback.invoke(origin, true, false);
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                cargando.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressView.setVisibility(View.VISIBLE);
                if (url.startsWith("https://accounts.google.com")) {
                    return false;
                } else if (url.startsWith("http:") || url.startsWith("https:")) {
                    return false;
                } else if (url.startsWith("whatsapp://")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setPackage("com.whatsapp");
                    try {
                        startActivity(intent);
                        if (mWebView.canGoBack()) {
                            mWebView.goBack();
                        }
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "WhatsApp not installed.", Toast.LENGTH_LONG).show();
                        if (mWebView.canGoBack()) {
                            mWebView.goBack();
                        }
                    }
                    return true;
                }

                // Otherwise allow the OS to handle things like tel, mailto, etc.
                try {
                    // Otherwise allow the OS to handle things like tel, mailto, etc.
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("WebView", "Unexpected error occurred while handling URL: " + url, e);
                    Toast.makeText(MainActivity.this, "Unable to handle URL: " + url, Toast.LENGTH_LONG).show();
                }
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // El servidor local usa un certificado de mkcert, que Android no reconoce. Solo
                // se acepta en debug y para *.localhost; en release se rechaza como siempre.
                String host = Uri.parse(error.getUrl()).getHost();
                if (BuildConfig.DEBUG && host != null && host.endsWith(".localhost")) {
                    handler.proceed();
                } else {
                    super.onReceivedSslError(view, handler, error);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                // Verifica si la URL no pertenece a tu dominio
                if (!url.contains(DOMAIN)) {
                    backButton.setVisibility(View.VISIBLE);
                    cargando.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    progressView.setVisibility(View.GONE);
                } else {
                    backButton.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                cargando.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                progressView.setVisibility(View.GONE);
                paginaCargada = true;

                if (locationTemporal != null) {
                    sendLocationToWebView(locationTemporal.getLatitude(), locationTemporal.getLongitude());
                }

                // Verifica si la URL no pertenece a tu dominio
                if (!url.contains(DOMAIN)) {
                    backButton.setVisibility(View.VISIBLE);
                } else {
                    backButton.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage) return;
            // parseResult entiende tanto una seleccion simple (getData) como multiple (ClipData)
            mUploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            mUploadMessage = null;
            mFileChooserParams = null;
        }
    }

    // Antes se pedia READ_EXTERNAL_STORAGE y solo se abria el selector si se concedia. Desde
    // Android 13 ese permiso ya no existe para apps con target 33+: el sistema lo deniega sin
    // preguntar, el selector no se abria nunca y el callback se quedaba sin responder (el
    // modal de la foto de perfil se cerraba sin hacer nada). El selector del sistema no
    // necesita ningun permiso: devuelve un content:// al que ya tenemos acceso.
    private void openFileExplorer() {
        Intent i;
        if (mFileChooserParams != null) {
            // Respeta el accept="" y el multiple del <input type="file"> de la web
            i = mFileChooserParams.createIntent();
        } else {
            i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
        }
        try {
            startActivityForResult(Intent.createChooser(i, "Selecciona un archivo"), FILECHOOSER_RESULTCODE);
        } catch (ActivityNotFoundException e) {
            Log.e("FileChooser", "No hay ninguna app para elegir archivos", e);
            if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(null);
                mUploadMessage = null;
            }
            Toast.makeText(this, "No se ha encontrado ninguna app para elegir archivos", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
        Intent intent = getIntent();
        String action = intent.getAction();
        String userID = stateChanges.getTo().getUserId();
        if (userID == null) {
            // OneSignal avisa primero sin suscripcion todavia: no hay nada que asociar y
            // recargar la web con userID=null solo le haria guardar un valor vacio.
            return;
        }
        if (intent.getData() != null) {
            // Si hemos interceptado alguna petición
            mWebView.loadUrl(intent.getData().toString());
        } else {
            // Sino pintamos el acceso por defecto
            // osApp: la app de OneSignal a la que pertenece la suscripcion. Un mismo usuario puede
            // tener la app de Wiplay y la de su club, cada una con su propio OneSignal, y el
            // servidor necesita saber con que app_id enviarle a cada suscripcion.
            System.out.println("Asociando app: " + userID + " (OneSignal " + ONESIGNAL_ID + ")");
            mWebView.loadUrl(URL + "?app=true" + parametrosSuscripcion());
        }
    }
}
