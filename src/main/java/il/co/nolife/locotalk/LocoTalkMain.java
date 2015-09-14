package il.co.nolife.locotalk;

import android.content.Intent;
import android.content.IntentSender;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;

import com.appspot.enhanced_cable_88320.aroundmeapi.model.GeoPt;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.UserAroundMe;
import com.aroundme.EndpointApiCreator;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import il.co.nolife.locotalk.DataTypes.EChatType;
import il.co.nolife.locotalk.DataTypes.LocoEvent;
import il.co.nolife.locotalk.DataTypes.LocoForum;
import il.co.nolife.locotalk.DataTypes.LocoUser;
import il.co.nolife.locotalk.ViewClasses.SimpleDialog;

// import com.google.android.gms.location.LocationListener;


public class LocoTalkMain extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, IMarkerLocoUserGetter, SimpleDialog.DialogClickListener {

    private static final String TAG = "LocoTalkMain";
    private static final int RC_SIGN_IN = 0;
    private boolean mIntentInProgress;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;

    HashMap<Marker, LocoUser> currentUserMarkers;
    HashMap<String, Marker> reverseMarkersMap;
    HashMap<Marker, LocoForum> forumMarkerMap;
    HashMap<Marker, LocoEvent> eventMarkerMap;
    Marker myMarker;

    Marker selectedMarker;
    Circle eventRadius;
    CameraPosition cameraPosition;

    BitmapDescriptor personMarkerIcon;
    BitmapDescriptor safePersonMarkerIcon;
    BitmapDescriptor friendMarkerIcon;
    BitmapDescriptor safeFriendMarkerIcon;
    BitmapDescriptor myMarkerIcon;
    BitmapDescriptor forumMarkerIcon;
    BitmapDescriptor eventMarkerIcon;

    Thread workerThread;
    Boolean pause = false;
    Boolean positionRetrieved = false;
    Boolean waitingForLocationServices = false;

    DataAccessObject dao;

    IApiCallback<String> newFriendListener;
    IApiCallback<String> userPongedListener;
    IApiCallback<Long> newForumListener;
    IApiCallback<Long> newEventListener;

    List<IApiCallback<Void>> waitingForMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Log.i(TAG, "it has to work!");
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        pause = false;
        EndpointApiCreator.initialize(null);
        setContentView(R.layout.map_activity);
        ApiHandler.Initialize(this);
        dao = new DataAccessObject(this);

        eventMarkerMap = new HashMap<>();
        forumMarkerMap = new HashMap<>();

        personMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.person));
        safePersonMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.person_blue));
        friendMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.person_yellow));
        safeFriendMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.person_green));
        myMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.person_red));
        forumMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.forum));
        eventMarkerIcon = BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.event));

        waitingForMap = new ArrayList<>();

        newFriendListener = new IApiCallback<String>() {
            @Override
            public void Invoke(String result) {

                AppController.SetFriends(dao.GetAllFriends());
                Marker m = reverseMarkersMap.get(result);
                if(m != null){
                    m.setIcon(friendMarkerIcon);
                }

            }
        };

        userPongedListener = new IApiCallback<String>() {
            @Override
            public void Invoke(String result) {

                Marker m = reverseMarkersMap.get(result);
                if (m != null) {
                    if(AppController.CheckIfFriend(result)) {
                        m.setIcon(safeFriendMarkerIcon);
                    } else {
                        m.setIcon(safePersonMarkerIcon);
                    }
                }

            }
        };

        buildGoogleApiClient();

    }

    protected void onStart() {
        super.onStart();

        AppController.SetFriends(dao.GetAllFriends());
        AppController.AddNewFriendListener(newFriendListener);
        AppController.AddUserPongedListener(userPongedListener);
        mGoogleApiClient.connect();
        LocationServiceEnabled();

    }

    protected void onStop() {
        super.onStop();

        AppController.RemoveNewFriendListener(newFriendListener);
        AppController.RemoveUserPongedListener(userPongedListener);
        AppController.RemoveForumsChangedListener(newForumListener);
        AppController.RemoveEventsChangedListener(newEventListener);

        pause = true;

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

    }

    protected void onResume() {
        super.onResume();

        if(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient) != null) {
            if(workerThread == null) {
                LocationServiceEnabled();
            }
        }

    }

    protected synchronized void buildGoogleApiClient(){

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        if (!mIntentInProgress && connectionResult.hasResolution()) {
                            try {
                                mIntentInProgress = true;
                                startIntentSenderForResult(connectionResult.getResolution().getIntentSender(),
                                        RC_SIGN_IN, null, 0, 0, 0);
                            } catch (IntentSender.SendIntentException e) {
                                // The intent was canceled before it was sent.  Return to the default
                                // state and attempt to connect to get an updated ConnectionResult.
                                mIntentInProgress = false;
                                mGoogleApiClient.connect();
                            }
                        }

                    }
                })
                .addApi(LocationServices.API)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .build();

    }

    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {

        if (requestCode == RC_SIGN_IN) {
            mIntentInProgress = false;

            if (!mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();

            }
        }

    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.

        if (mMap == null) {

            Log.i(TAG, "map was Null");
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {

                mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                    @Override
                    public void onInfoWindowClick(Marker marker) {
                        MarkerClicked(marker);
                    }
                });
                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        LongMapClicked(latLng);
                    }
                });

                mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        //MarkerClicked(marker);
                        LocoEvent event = eventMarkerMap.get(marker);
                        if (event != null) {
                            selectedMarker = marker;

                            eventRadius = mMap.addCircle(new CircleOptions()
                                    .center(new LatLng(event.getLocation().getLatitude(), event.getLocation().getLongitude()))
                                    .radius(20000)
                                    .strokeWidth(3)
                                    .strokeColor(Color.parseColor("#aabbdd")));

                            Log.i("ClickCheck", "Selecting " + selectedMarker.getTitle());
                        }
                        return false;
                    }
                });

                mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {
                        if (selectedMarker != null) {
                            Log.i("ClickCheck", "Deselecting " + selectedMarker.getTitle());
                            selectedMarker = null;
                            if (eventRadius != null) {
                                eventRadius.remove();
                                eventRadius = null;
                            }
                        }
                    }
                });

                onMapReady();

            }

        }

    }

    void LongMapClicked (LatLng latLng){
        EventOrForumActivity.Reset();
        Intent chooseIntent = new Intent(this, EventOrForumActivity.class);
        chooseIntent.putExtra("lon",latLng.longitude);
        chooseIntent.putExtra("lat",latLng.latitude);
        startActivity(chooseIntent);
    }

    void MarkerClicked(Marker marker){

        LocoUser user = currentUserMarkers.get(marker);
        if(user != null){
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("type", EChatType.PRIVATE.ordinal());
            chatIntent.putExtra("from", user.getMail());
            startActivity(chatIntent);
        } else {

            LocoEvent event = eventMarkerMap.get(marker);
            if(event != null) {

                Intent chatIntent = new Intent(this, ChatActivity.class);
                chatIntent.putExtra("type", EChatType.EVENT.ordinal());
                chatIntent.putExtra("eventId", event.getId());
                startActivity(chatIntent);

            } else {

                LocoForum forum = forumMarkerMap.get(marker);
                if(forum != null) {

                    Intent chatIntent = new Intent(this, ChatActivity.class);
                    chatIntent.putExtra("type", EChatType.FORUM.ordinal());
                    chatIntent.putExtra("forumId", forum.getId());
                    startActivity(chatIntent);

                } else {
                    Log.e(TAG, "Could not find marker in the current maps");
                }

            }

        }

    }

    @Override
    public void onConnected(Bundle bundle) {

        Log.i("This is bull shit", Plus.PeopleApi.getCurrentPerson(mGoogleApiClient).toString());

        if (Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) != null) {

            Log.i(getClass().toString(),"On Connect2");
            Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            String personName = currentPerson.getDisplayName();
            String personPhoto = currentPerson.getImage().getUrl();
            String personEmail = Plus.AccountApi.getAccountName(mGoogleApiClient);

            LocoUser myUser = new LocoUser();

            myUser.setName(personName);
            if (personEmail.compareTo("nir.jackson89@gmail.com")==0){
                personEmail="nir.jackson890@gmail.com";
            }else if (personEmail.compareTo("iloriginal@gmail.com")==0){
                personEmail="iloriginal0@gmail.com";
            }

            myUser.setMail(personEmail);
            myUser.setIcon(personPhoto);
            AppController.SetMyUser(myUser);
            // Log.i(TAG, myUser.toString());

            ApiHandler.GetRegistrationId(new IApiCallback<String>() {
                @Override
                public void Invoke(String result) {
                    AppController.GetMyUser().setRegId(result);
                    Log.i(TAG, AppController.GetMyUser().toString());
                    ApiHandler.Login(AppController.GetMyUser().toUser(), new IApiCallback<Boolean>() {
                        @Override
                        public void Invoke(Boolean result) {
                            if(result) {
                                Log.i(TAG, "Successfully logged in to AroundMe backend");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        findViewById(R.id.splash).setVisibility(View.GONE);
                                    }
                                });
                            } else {
                                Log.e(TAG, "Failed to log in to the AroundMe backend");
                            }
                        }
                    });
                }
            });

            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(mLastLocation == null) {

                if(!waitingForLocationServices) {

                    Bundle dialogBundle = new Bundle();
                    dialogBundle.putString("title", "Location");
                    dialogBundle.putString("positive", "Go");
                    dialogBundle.putString("negative", "Later");
                    dialogBundle.putString("content", "Your location services are turned off, this application cannot operate without access to your current location.\nPlease turn on location services");
                    SimpleDialog d = new SimpleDialog();
                    d.setArguments(dialogBundle);
                    d.show(getFragmentManager(), "SimpleDialog");

                }

            }

        }

        setUpMapIfNeeded();

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void onMapReady() {

        for (IApiCallback<Void> c : waitingForMap) {
            c.Invoke(null);
        }

        newForumListener = new IApiCallback<Long>() {
            @Override
            public void Invoke(final Long result) {
                RefreshForumMarkers();
            }
        };

        newEventListener = new IApiCallback<Long>() {
            @Override
            public void Invoke(Long result) {
                RefreshEventMarkers();
            }
        };

        AppController.AddForumsChangedListener(newForumListener);
        AppController.AddEventsChangedListener(newEventListener);

        for (Marker marker : eventMarkerMap.keySet()) {
            marker.remove();
        }

        RefreshForumMarkers();
        RefreshEventMarkers();

    }

    public void RefreshForumMarkers() {

        if(forumMarkerMap != null) {
            for (Marker marker : forumMarkerMap.keySet()) {
                marker.remove();
            }
        }

        forumMarkerMap = new HashMap<>();
        List<LocoForum> forums = dao.GetAllForums();

        for (LocoForum forum : forums) {

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(forum.getLocation().getLatitude(), forum.getLocation().getLongitude()))
                    .icon(forumMarkerIcon)
                    .title(forum.getName()));

            forumMarkerMap.put(marker, forum);

        }

    }

    public void RefreshEventMarkers() {

        if(eventMarkerMap != null) {
            for (Marker marker : eventMarkerMap.keySet()) {
                marker.remove();
            }
        }

        eventMarkerMap = new HashMap<>();
        List<LocoEvent> events = dao.GetAllEvents();

        for (LocoEvent event : events) {

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(event.getLocation().getLatitude(), event.getLocation().getLongitude()))
                    .icon(eventMarkerIcon)
                    .title(event.getName()));

            Log.i("EVENTCHECK", "event name: " + event.getName());

            eventMarkerMap.put(marker, event);

        }

    }

    public void LocationServiceEnabled() {

        pause = false;
        Log.i(TAG, "Starting worker");
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, pause.toString());
                while(!pause) {

                    Log.i(TAG, "Worker thread tick");
                    final Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

                    if(mLastLocation != null) {

                        final GeoPt myLoc = new GeoPt();
                        myLoc.setLatitude((float) mLastLocation.getLatitude());
                        myLoc.setLongitude((float) mLastLocation.getLongitude());

                        AppController.GetMyUser().setLocation(myLoc);
                        ApiHandler.SetMyLocation(myLoc);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if (myMarker == null) {
                                    if (mMap != null) {

                                        myMarker = mMap.addMarker(new MarkerOptions()
                                                .title(AppController.GetMyUser().getName())
                                                .position(new LatLng(myLoc.getLatitude(), myLoc.getLongitude()))
                                                .icon(myMarkerIcon));

                                        cameraPosition = new CameraPosition.Builder()
                                                .target(myMarker.getPosition())
                                                .zoom(11)
                                                .bearing(0)
                                                .tilt(0)
                                                .build();

                                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                                        positionRetrieved = true;

                                    }
                                } else {
                                    myMarker.setPosition(new LatLng(myLoc.getLatitude(), myLoc.getLongitude()));
                                }

                                ApiHandler.GetAllUsers(AppController.GetMyUser().getMail(), new IApiCallback<List<UserAroundMe>>() {
                                    @Override
                                    public void Invoke(List<UserAroundMe> result) {
                                        if (result != null) {
                                            final List<UserAroundMe> fResult = result;
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {

                                                    // Log.i(TAG, fResult.toString());
                                                    if (currentUserMarkers != null) {
                                                        for (Map.Entry<Marker, LocoUser> p : currentUserMarkers.entrySet()) {
                                                            p.getKey().remove();
                                                        }
                                                    }
                                                    currentUserMarkers = new HashMap<Marker, LocoUser>();
                                                    reverseMarkersMap = new HashMap<String, Marker>();

                                                    for (UserAroundMe u : fResult) {

                                                        if (u.getMail().compareTo(AppController.GetMyUser().getMail()) != 0) {

                                                            LocoUser nUser = new LocoUser(u);
                                                            AppController.AddUserToCache(nUser);

                                                            // Log.i(TAG, nUser.toString());
                                                            if (nUser.getLocation() != null) {

                                                                MarkerOptions options = new MarkerOptions()
                                                                        .position(new LatLng(nUser.getLocation().getLatitude(), nUser.getLocation().getLongitude()))
                                                                        .title(nUser.getName());

                                                                if(AppController.CheckIfSafeFriend(nUser.getMail())) {
                                                                    options.icon(safeFriendMarkerIcon);
                                                                } else if(AppController.CheckIfFriend(nUser.getMail())) {
                                                                    options.icon(friendMarkerIcon);
                                                                } else {
                                                                    options.icon(personMarkerIcon);
                                                                }

                                                                Marker newMarker = mMap.addMarker(options);
                                                                currentUserMarkers.put(newMarker, nUser);
                                                                reverseMarkersMap.put(nUser.getMail(), newMarker);

                                                            }

                                                        }

                                                    }

                                                    AppController.UpdateFriendsLocation(currentUserMarkers.values(), dao);

                                                }

                                            });

                                        }

                                    }

                                });



                            }

                        });

                        try {
                            Thread.sleep(30000, 0);
                        } catch (InterruptedException e) {
                            if(workerThread != null) {
                                Log.e("TickerThread", e.getMessage());
                                e.printStackTrace();
                            }
                        }

                    } else {

                        try {
                            Thread.sleep(1000, 0);
                        } catch (InterruptedException e) {
                            if(workerThread != null) {
                                Log.e("TickerThread", e.getMessage());
                                e.printStackTrace();
                            }
                        }

                    }

                }

            }
        });

        workerThread.start();

    }

    @Override
    public LocoUser GetUser(Marker marker) {
        return currentUserMarkers.get(marker);
    }

    public void RefreshFriends() {

        DataAccessObject dao = new DataAccessObject(this);
        dao.GetAllFriends();

    }

    @Override
    public void onPositive() {

        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(intent);
        waitingForLocationServices = true;

    }

    @Override
    public void onNegative() {
        waitingForLocationServices = true;
    }

}
