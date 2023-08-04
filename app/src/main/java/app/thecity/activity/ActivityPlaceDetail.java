package app.thecity.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import app.thecity.AppConfig;
import app.thecity.R;
import app.thecity.adapter.AdapterImageList;

import app.thecity.connection.RestAdapter;
import app.thecity.connection.callbacks.CallbackPlaceDetails;
import app.thecity.data.Constant;
import app.thecity.data.DatabaseHandler;
import app.thecity.data.SharedPref;
import app.thecity.data.ThisApplication;
import app.thecity.model.Images;
import app.thecity.model.Place;
import app.thecity.utils.Tools;
import retrofit2.Call;
import retrofit2.Response;

/*
     Android-Aktivität (Activity), die die Details zu einem bestimmten Ort anzeigt
 */

public class ActivityPlaceDetail extends AppCompatActivity {

    private static final String EXTRA_OBJ = "key.EXTRA_OBJ";
    private static final String EXTRA_NOTIF_FLAG = "key.EXTRA_NOTIF_FLAG";

    /*
      Eine statische Methode zum Navigieren zur ActivityPlaceDetail von einer anderen Aktivität aus.
      Sie akzeptiert die Startaktivität (activity), eine freigegebene Ansicht (sharedView) für die
      Aktivitätstransition und ein Place-Objekt (p), das die Details des Ortes enthält.
     */
    public static void navigate(AppCompatActivity activity, View sharedView, Place p) {
        Intent intent = new Intent(activity, ActivityPlaceDetail.class);
        intent.putExtra(EXTRA_OBJ, p);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, sharedView, EXTRA_OBJ);
        ActivityCompat.startActivity(activity, intent, options.toBundle());
    }

    /*
      Eine statische Methode zum Erstellen des Absichtsobjekts zum Navigieren zur ActivityPlaceDetail.
      Sie akzeptiert das Kontextobjekt, das Place-Objekt (obj) und einen booleschen Wert (from_notif),
      der angibt, ob die Aktivität von einer Benachrichtigung gestartet wurde
     */
    public static Intent navigateBase(Context context, Place obj, Boolean from_notif) {
        Intent i = new Intent(context, ActivityPlaceDetail.class);
        i.putExtra(EXTRA_OBJ, obj);
        i.putExtra(EXTRA_NOTIF_FLAG, from_notif);
        return i;
    }

    private Place place = null;
    private FloatingActionButton fab;
    private WebView description = null;
    private View parent_view = null;
    private GoogleMap googleMap;
    private DatabaseHandler db;

    private boolean onProcess = false;
    private boolean isFromNotif = false;
    private Call<CallbackPlaceDetails> callback;
    private View lyt_progress;
    private View lyt_distance;
    private float distance = -1;
    private Snackbar snackbar;
    private ArrayList<String> new_images_str = new ArrayList<>();

    // Initialisiert die Ansicht, die Toolbar und die Google Map.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_details);
        parent_view = findViewById(android.R.id.content);

        db = new DatabaseHandler(this);
        // animation transition
        ViewCompat.setTransitionName(findViewById(R.id.app_bar_layout), EXTRA_OBJ);

        place = (Place) getIntent().getSerializableExtra(EXTRA_OBJ);
        isFromNotif = getIntent().getBooleanExtra(EXTRA_NOTIF_FLAG, false);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        lyt_progress = findViewById(R.id.lyt_progress);
        lyt_distance = findViewById(R.id.lyt_distance);
        if (place.image != null) {
            Tools.displayImage(this, (ImageView) findViewById(R.id.image), Constant.getURLimgPlace(place.image));
        }

        fabToggle();
        setupToolbar(place.name == null ? "" : place.name);
        initMap();


        // handle when favorite button clicked
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (db.isFavoritesExist(place.place_id)) {
                    db.deleteFavorites(place.place_id);
                    Snackbar.make(parent_view, place.name + " " + getString(R.string.remove_favorite), Snackbar.LENGTH_SHORT).show();
                    // analytics tracking
                    ThisApplication.getInstance().trackEvent(Constant.Event.FAVORITES.name(), "REMOVE", place.name);
                } else {
                    db.addFavorites(place.place_id);
                    Snackbar.make(parent_view, place.name + " " + getString(R.string.add_favorite), Snackbar.LENGTH_SHORT).show();
                    // analytics tracking
                    ThisApplication.getInstance().trackEvent(Constant.Event.FAVORITES.name(), "ADD", place.name);
                }
                fabToggle();
            }
        });

        // for system bar in lollipop
        Tools.systemBarLolipop(this);
        Tools.RTLMode(getWindow());

        // analytics tracking
        ThisApplication.getInstance().trackScreenView("View place : " + (place.name == null ? "name" : place.name));
    }


    /*
      Zeigt die Daten des angegebenen Place-Objekts an, einschließlich Name, Adresse, Telefon,
      Website, Beschreibung und Bildergalerie.
     */
    private void displayData(Place p) {

        setupToolbar(place.name);
        Tools.displayImage(this, (ImageView) findViewById(R.id.image), Constant.getURLimgPlace(place.image));

        ((TextView) findViewById(R.id.address)).setText(p.address);
        ((TextView) findViewById(R.id.phone)).setText(p.phone.equals("-") || p.phone.trim().equals("") ? getString(R.string.no_phone_number) : p.phone);
        ((TextView) findViewById(R.id.website)).setText(p.website.equals("-") || p.website.trim().equals("") ? getString(R.string.no_website) : p.website);

        description = (WebView) findViewById(R.id.description);
        String html_data = "<style>img{max-width:100%;height:auto;} iframe{width:100%;}</style> ";
        html_data += p.description;
        description.getSettings().setBuiltInZoomControls(true);
        description.setBackgroundColor(Color.TRANSPARENT);
        description.setWebChromeClient(new WebChromeClient());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            description.loadDataWithBaseURL(null, html_data, "text/html; charset=UTF-8", "utf-8", null);
        } else {
            description.loadData(html_data, "text/html; charset=UTF-8", null);
        }
        description.getSettings().setJavaScriptEnabled(true);
        // disable scroll on touch
        description.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return (event.getAction() == MotionEvent.ACTION_MOVE);
            }
        });

        distance = place.distance;

        if (distance == -1) {
            lyt_distance.setVisibility(View.GONE);
        } else {
            lyt_distance.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.distance)).setText(Tools.getFormatedDistance(distance));
        }

        setImageGallery(db.getListImageByPlaceId(p.place_id));
    }

    /*
       Wird aufgerufen, wenn die Aktivität wieder aufgenommen wird. Hier wird loadPlaceData()
       aufgerufen, um die Daten des Ortes anzuzeigen.
     */
    @Override
    protected void onResume() {
        loadPlaceData();
        if (description != null) description.onResume();
        super.onResume();
    }

    /*
      Eine Methode, die aufgerufen wird, wenn auf bestimmte Layout-Elemente wie Adresse, Telefon
      oder Website geklickt wird, um die entsprechenden Aktionen auszuführen.
     */
    public void clickLayout(View view) {
        int id = view.getId();
        if (id == R.id.lyt_address) {
            if (!place.isDraft()) {
                Uri uri = Uri.parse("http://maps.google.com/maps?q=loc:" + place.lat + "," + place.lng);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        } else if (id == R.id.lyt_phone) {
            if (!place.isDraft() && !place.phone.equals("-") && !place.phone.trim().equals("")) {
                Tools.dialNumber(this, place.phone);
            } else {
                Snackbar.make(parent_view, R.string.fail_dial_number, Snackbar.LENGTH_SHORT).show();
            }
        } else if (id == R.id.lyt_website) {
            if (!place.isDraft() && !place.website.equals("-") && !place.website.trim().equals("")) {
                Tools.directUrl(this, place.website);
            } else {
                Snackbar.make(parent_view, R.string.fail_open_website, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    /*
      Zeigt eine Bildergalerie des Ortes an, die aus Bildern besteht, die im Place-Objekt enthalten sind.
     */
    private void setImageGallery(List<Images> images) {
        // add optional image into list
        List<Images> new_images = new ArrayList<>();
        new_images.add(new Images(place.place_id, place.image));
        new_images.addAll(images);
        new_images_str = new ArrayList<>();
        for (Images img : new_images) {
            new_images_str.add(Constant.getURLimgPlace(img.name));
        }

        RecyclerView galleryRecycler = (RecyclerView) findViewById(R.id.galleryRecycler);
        galleryRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        AdapterImageList adapter = new AdapterImageList(this, new_images);
        galleryRecycler.setAdapter(adapter);
        adapter.setOnItemClickListener(new AdapterImageList.OnItemClickListener() {
            @Override
            public void onItemClick(View view, String viewModel, int pos) {
                openImageGallery(pos);
            }
        });
    }

    // Öffnet die Bildergalerie mit dem angegebenen Startbild.
    private void openImageGallery(int position) {
        Intent i = new Intent(ActivityPlaceDetail.this, ActivityFullScreenImage.class);
        i.putExtra(ActivityFullScreenImage.EXTRA_POS, position);
        i.putStringArrayListExtra(ActivityFullScreenImage.EXTRA_IMGS, new_images_str);
        startActivity(i);
    }

    /*
      Ändert das Symbol des FloatingActionButton (fab) basierend auf dem Vorhandensein
      des Orts in der Favoritenliste.
     */
    private void fabToggle() {
        if (db.isFavoritesExist(place.place_id)) {
            fab.setImageResource(R.drawable.ic_nav_favorites);
        } else {
            fab.setImageResource(R.drawable.ic_nav_favorites_outline);
        }
    }

    // Konfiguriert die Toolbar und CollapsingToolbarLayout und zeigt den Namen des Ortes an.

    private void setupToolbar(String name) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle("");

        ((TextView) findViewById(R.id.toolbar_title)).setText(name);

        final CollapsingToolbarLayout collapsing_toolbar = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        collapsing_toolbar.setContentScrimColor(new SharedPref(this).getThemeColorInt());
        ((AppBarLayout) findViewById(R.id.app_bar_layout)).addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (collapsing_toolbar.getHeight() + verticalOffset < 2 * ViewCompat.getMinimumHeight(collapsing_toolbar)) {
                    fab.show();
                } else {
                    fab.hide();
                }
            }
        });

        (findViewById(R.id.image)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (new_images_str == null || new_images_str.size() <= 0) return;
                openImageGallery(0);
            }
        });
    }

    // Erstellt das Optionsmenü in der Aktionsleiste.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_details, menu);
        return true;
    }

    // Reagiert auf Klicks auf die Menüelemente, z. B. das Teilen des Ortes.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            backAction();
            return true;
        } else if (id == R.id.action_share) {
            if (!place.isDraft()) {
                Tools.methodShare(ActivityPlaceDetail.this, place);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    // Initialisiert die Google Map und konfiguriert die Kartenansicht.
    private void initMap() {
        if (googleMap == null) {
            MapFragment mapFragment1 = (MapFragment) getFragmentManager().findFragmentById(R.id.mapPlaces);
            mapFragment1.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap gMap) {
                    googleMap = gMap;
                    if (googleMap == null) {
                        Snackbar.make(parent_view, R.string.unable_create_map, Snackbar.LENGTH_SHORT).show();
                    } else {
                        // config map
                        googleMap = Tools.configStaticMap(ActivityPlaceDetail.this, googleMap, place);
                    }
                }
            });
        }

        ((Button) findViewById(R.id.bt_navigate)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Toast.makeText(getApplicationContext(),"OPEN", Toast.LENGTH_LONG).show();
                Intent navigation = new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?daddr=" + place.lat + "," + place.lng));
                startActivity(navigation);
            }
        });
        ((Button) findViewById(R.id.bt_view)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPlaceInMap();
            }
        });
        ((LinearLayout) findViewById(R.id.map)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPlaceInMap();
            }
        });
    }

    // Öffnet den ausgewählten Ort in der Google Maps-Anwendung.
    private void openPlaceInMap() {
        Intent intent = new Intent(ActivityPlaceDetail.this, ActivityMaps.class);
        intent.putExtra(ActivityMaps.EXTRA_OBJ, place);
        startActivity(intent);
    }

    /*
      Wird aufgerufen, wenn die Aktivität zerstört wird. Hier wird überprüft, ob der API-Aufruf
      noch ausgeführt wird und gegebenenfalls abgebrochen
     */
    @Override
    protected void onDestroy() {
        if (callback != null && callback.isExecuted()) callback.cancel();
        super.onDestroy();
    }

    /*
     Wird aufgerufen, wenn die Zurück-Taste des Geräts gedrückt wird. Hier wird festgelegt,
     wie die Aktivität beendet wird, basierend auf dem Wert von isFromNotif
     */
    @Override
    public void onBackPressed() {
        backAction();
    }

    /*
      Wird aufgerufen, wenn die Aktivität pausiert wird. Hier wird sichergestellt,
      dass die WebView pausiert wird, um Ressourcen zu sparen.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (description != null) description.onPause();
    }

    /*
      Eine Hilfsmethode, die festlegt, wie die Aktivität beendet wird, abhängig davon,
      ob sie von einer Benachrichtigung gestartet wurde oder nicht
     */
    private void backAction() {
        if (isFromNotif) {
            Intent i = new Intent(this, ActivityMain.class);
            startActivity(i);
        }
        finish();
    }

    /*
      Lädt die Ortsdaten und zeigt sie an. Wenn der Ort noch nicht in der Datenbank gespeichert ist,
      wird versucht, die Daten von der API abzurufen.
     */
    private void loadPlaceData() {
        place = db.getPlace(place.place_id);
        if (place.isDraft()) {
            if (Tools.cekConnection(this)) {
                requestDetailsPlace(place.place_id);
            } else {
                onFailureRetry(getString(R.string.no_internet));
            }
        } else {
            displayData(place);
        }
    }

    // Ruft die Detaildaten des Ortes von der API ab.
    private void requestDetailsPlace(int place_id) {
        if (onProcess) {
            Snackbar.make(parent_view, R.string.task_running, Snackbar.LENGTH_SHORT).show();
            return;
        }
        onProcess = true;
        showProgressbar(true);
        callback = RestAdapter.createAPI().getPlaceDetails(place_id);
        callback.enqueue(new retrofit2.Callback<CallbackPlaceDetails>() {
            @Override
            public void onResponse(Call<CallbackPlaceDetails> call, Response<CallbackPlaceDetails> response) {
                CallbackPlaceDetails resp = response.body();
                if (resp != null) {
                    place = db.updatePlace(resp.place);
                    displayDataWithDelay(place);
                } else {
                    onFailureRetry(getString(R.string.failed_load_details));
                }

            }

            @Override
            public void onFailure(Call<CallbackPlaceDetails> call, Throwable t) {
                if (call != null && !call.isCanceled()) {
                    boolean conn = Tools.cekConnection(ActivityPlaceDetail.this);
                    if (conn) {
                        onFailureRetry(getString(R.string.failed_load_details));
                    } else {
                        onFailureRetry(getString(R.string.no_internet));
                    }
                }
            }
        });
    }

    /*
      Zeigt die Daten des Ortes mit einer leichten Verzögerung an,
      um die Benutzeroberfläche responsiver zu machen.
     */
    private void displayDataWithDelay(final Place resp) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                showProgressbar(false);
                onProcess = false;
                displayData(resp);
            }
        }, 1000);
    }

    /*
      Zeigt eine Snackbar-Nachricht mit einer Wiederholungsoption an,
      wenn das Laden der Daten fehlgeschlagen ist.
     */
    private void onFailureRetry(final String msg) {
        showProgressbar(false);
        onProcess = false;
        snackbar = Snackbar.make(parent_view, msg, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.RETRY, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadPlaceData();
            }
        });
        snackbar.show();
        retryDisplaySnackbar();
    }

    // Zeigt die Snackbar-Nachricht periodisch erneut an, wenn sie noch nicht angezeigt wird
    private void retryDisplaySnackbar() {
        if (snackbar != null && !snackbar.isShown()) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    retryDisplaySnackbar();
                }
            }, 1000);
        }
    }

    // Zeigt oder verbirgt die Fortschrittsanzeige.
    private void showProgressbar(boolean show) {
        lyt_progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
