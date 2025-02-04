package il.co.nolife.locotalk;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.appspot.enhanced_cable_88320.aroundmeapi.Aroundmeapi;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.GeoPt;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.Message;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.User;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.UserAroundMe;
import com.appspot.enhanced_cable_88320.aroundmeapi.model.UserLocation;
import com.aroundme.EndpointApiCreator;
import com.aroundme.deviceinfoendpoint.Deviceinfoendpoint;
import com.aroundme.deviceinfoendpoint.model.DeviceInfo;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.api.client.util.DateTime;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import il.co.nolife.locotalk.DataTypes.LocoEvent;
import il.co.nolife.locotalk.DataTypes.LocoForum;
import il.co.nolife.locotalk.DataTypes.LocoUser;

/**
 * Created by Victor Belski on 9/7/2015.
 * Handles all operation involving the AroundMeApi
 */
public class ApiHandler {

    public static final String TAG = "ApiHandler";

    static {
        instance = new ApiHandler();
    }

    public static final String SENDER_ID = "1047488186224";
    static ApiHandler instance;

    Aroundmeapi api;
    Context context;
    String regId;
    LocoTalkSharedPreferences prefs;
    User user;

    List<Callback<Void>> delayedCalls;

    boolean initialized = false;

    ApiHandler() {

        EndpointApiCreator.initialize(null);
        delayedCalls = new ArrayList<Callback<Void>>();
        initialized = false;

        try {
            api = EndpointApiCreator.getApi(Aroundmeapi.class);
        } catch (Exception e) {
            Log.e(getClass().toString(), e.getMessage());
        }

    }

    public static void Initialize(Context context) {

        if(!instance.initialized) {

            instance.prefs = new LocoTalkSharedPreferences(context);
            instance.context = context;
            instance.user = instance.prefs.GetUser();
            instance.RegisterAsync(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    instance.initialized = true;
                    for (Callback<Void> c : instance.delayedCalls) {
                        c.Invoke(null);
                    }
                    Log.i(getClass().toString(), "Successfully initialized");
                    instance.delayedCalls = new ArrayList<Callback<Void>>();
                }
            });

        }

    }

    public static void GetRegistrationId(final Callback<String> callback) {

        if(instance.initialized) {
            callback.Invoke(instance.regId);
        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    callback.Invoke(instance.regId);
                }
            });

        }

    }

    public static void RetrieveMessage(final long messageId, final Callback<Message> onEnd) {

        if (instance.initialized) {
            instance.RetrieveMessageAsync(messageId, onEnd);
        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    RetrieveMessage(messageId, onEnd);
                }
            });

        }

    }

    public static void SendGCMMessage(final String mail, final String message) {

        if(instance.initialized) {
            instance.SendGCMMessageAsync(mail, message);
        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    SendGCMMessage(mail, message);
                }
            });

        }

    }

    public static User GetUser() {
        return instance.prefs.GetUser();
    }

    public static void SetMyLocation(GeoPt loc) {

        instance.user.setLocation(new UserLocation().setPoint(loc));
        instance.prefs.StoreUser(instance.user);
        instance.ReportLocationAsync(loc, null);

    }

    public static void Login(final User user, final Callback<Boolean> callback) {

        Log.i(instance.getClass().toString(), "Trying to login");

        if(instance.initialized) {

            String pass = "abcde";//instance.prefs.GetPassword();
            User u2 = user;
            u2.setPassword(pass);
            Log.i("user password", pass);
//            if(pass.isEmpty()) {
//                instance.Register(user, callback);
//            }
            instance.LoginAsync(u2, new Callback<User>() {
                @Override
                public void Invoke(User result) {

                    if (result != null) {

                        instance.user = result;
                        instance.prefs.StoreUser(result);

                        if (callback != null) {
                            callback.Invoke(true);
                        } else {
                            Log.e(TAG, "Callback was null on login");
                        }

                    } else {
                        Log.i(TAG, "Login falied, atempting to resgister");
                        instance.Register(user, callback);
                    }

                }
            });
        } else {
            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    Login(user, callback);
                }
            });
            Log.i(instance.getClass().toString(), "Registering delayed Login");
        }

    }

    void Register(final User user, final Callback<Boolean> callback) {

        if(instance.initialized) {
            //SecureRandom random = new SecureRandom();
            final String pass = "abcde";
            user.setPassword(pass);
            instance.RegisterUserAsync(user, new Callback<User>() {
                @Override
                public void Invoke(User result) {
                    if (result != null) {

                        instance.prefs.StorePassword(pass);
                        result.setPassword(pass);
                        instance.LoginAsync(result, new Callback<User>() {
                            @Override
                            public void Invoke(User result) {

                                if (result != null) {

                                    instance.user = result;
                                    instance.prefs.StoreUser(result);

                                    if (callback != null) {
                                        callback.Invoke(true);
                                    } else {
                                        Log.e(TAG, "Callback was null on Register");
                                    }
                                } else {
                                    if (callback != null) {
                                        callback.Invoke(false);
                                    } else {
                                        Log.e(TAG, "Callback was null on register");
                                    }
                                }

                            }
                        });

                    } else {
                        if (callback != null) {
                            callback.Invoke(false);
                        } else {
                            Log.e(TAG, "Callback was null on registar");
                        }
                    }
                }
            });

        } else {
            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    Register(user, callback);
                    Log.i(instance.getClass().toString(), "Registering delayed Register");
                }
            });
        }

    }

    public static void SendMessageToUser(final Message message, final Callback<Boolean> onEnd) {

        if(instance.initialized) {
            instance.SendStructuredMessageAsync(message, onEnd);
        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    SendMessageToUser(message, onEnd);
                }
            });

        }

    }

    public static void Ping(final String mail) {

        if(instance.initialized) {
            instance.SendGCMMessageAsync(mail, "{ 'type': 'ping', 'mail': '" + AppController.GetMyUser().getMail() + "' }");
            Log.i(TAG, "Sent ping to " + mail);
        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    Ping(mail);
                }
            });

        }

    }

    public static void GetUsersAroundMe(final GeoPt point, final int radius, final String mail, final Callback<List<UserAroundMe>> onEnd) {

        if(instance.initialized) {
            instance.GetUsersAroundMeAsync(point, radius, mail, onEnd);
        } else {
            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    GetUsersAroundMe(point, radius, mail, onEnd);
                }
            });
        }

    }

    public static void CreateForum(final LocoForum forum) {

        if(instance.initialized) {

            StringBuilder builder = new StringBuilder();

            builder.append("{ 'type' : 'newForum', 'forumId':");
            builder.append(forum.getId());
            builder.append(", 'owner':'");
            builder.append(forum.getOwner());
            builder.append("', 'name':'");
            builder.append(forum.getName());
            builder.append("', 'lat':");
            builder.append(forum.getLocation().getLatitude());
            builder.append(", 'lon':");
            builder.append(forum.getLocation().getLongitude());
            builder.append(", 'participants': [");
            boolean first = true;
            for (LocoUser u : forum.getUsers()) {
                if(first) {
                    builder.append("{ 'mail':'");
                    first = false;
                } else {
                    builder.append(", { 'mail':'");
                }
                builder.append(u.getMail());
                builder.append("', 'name':'");
                builder.append(u.getName());
                builder.append("', 'icon':'");
                builder.append(u.getIcon());
                builder.append("' }");
            }
            builder.append("] }");

            String json = builder.toString();
            String myMail = AppController.GetMyUser().getMail();
            Log.i(TAG, json);
            for (LocoUser user : forum.getUsers()) {

                String mail = user.getMail();
                if(myMail.compareTo(mail) != 0) {
                    instance.SendGCMMessageAsync(user.getMail(), json);
                }
            }

        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    CreateForum(forum);
                }
            });

        }

    }

    public static void SendForumMessage(final LocoForum forum, final String message) {

        if(instance.initialized) {

            StringBuilder builder = new StringBuilder();

            builder.append("{ 'type' : 'forumMessage', 'forumId':");
            builder.append(forum.getId());
            builder.append(", 'owner':'");
            builder.append(forum.getOwner());
            builder.append("', 'message': { 'from':'");
            builder.append(AppController.GetMyUser().getMail());
            builder.append("', 'time':");
            builder.append(new Date().getTime());
            builder.append(", 'content':'");
            builder.append(message);
            builder.append("' } }");

            String json = builder.toString();

            Log.i("CHECK FORUM", json);

            String myMail = AppController.GetMyUser().getMail();
            for (LocoUser user : forum.getUsers()) {
                String mail = user.getMail();
                if(mail.compareTo(myMail) != 0) {
                    instance.SendGCMMessageAsync(user.getMail(), json);
                }
            }

        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    SendForumMessage(forum, message);
                }
            });

        }

    }

    public static void SendEventMessage(final LocoEvent event, final String message) {

        if(instance.initialized) {

            StringBuilder builder = new StringBuilder();

            builder.append("{ 'type' : 'eventMessage', 'eventId':");
            builder.append(event.getId());
            builder.append(", 'owner':'");
            builder.append(event.getOwner());
            builder.append("', 'radius':");
            builder.append(event.getRadius());
            builder.append(", 'lat':");
            builder.append(event.getLocation().getLatitude());
            builder.append(", 'lon':");
            builder.append(event.getLocation().getLongitude());
            builder.append(", 'name':'");
            builder.append(event.getName());
            builder.append("', 'message': { 'from':'");
            builder.append(AppController.GetMyUser().getMail());
            builder.append("', 'time':");
            builder.append(new Date().getTime());
            builder.append(", 'content':'");
            builder.append(message);
            builder.append("' } }");

            final String json = builder.toString();

            Log.i(TAG, event.toString());

            instance.GetUsersAroundPoint(event.getRadius(), event.getLocation().getLatitude(), event.getLocation().getLongitude(), new Callback<List<UserAroundMe>>() {
                @Override
                public void Invoke(List<UserAroundMe> result) {
                    Log.i(TAG, result.toString());
                    if(result != null) {
                        String myMail = AppController.GetMyUser().getMail();
                        Log.i(TAG, "Sendding to users:");
                        for (UserAroundMe user : result) {
                            String mail = user.getMail();
                            Log.i(TAG, mail);
                            if(myMail.compareTo(mail) != 0) {
                                Log.i(TAG, mail);
                                instance.SendGCMMessageAsync(user.getMail(), json);
                            }
                        }
                    }
                }
            });

        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    SendEventMessage(event, message);
                }
            });

        }

    }

    public static void RemoveFromForum(final LocoForum forum) {

        if(instance.initialized) {

            StringBuilder builder = new StringBuilder();

            builder.append("{ 'type' : 'removeForum', 'forumId':");
            builder.append(forum.getId());
            builder.append(", 'owner':'");
            builder.append(forum.getOwner());
            builder.append("', 'user':");
            builder.append(AppController.GetMyUser().getMail());
            builder.append("' }");

            final String json = builder.toString();

            String myMail = AppController.GetMyUser().getMail();
            for (LocoUser user : forum.getUsers()) {

                if(myMail.compareTo(user.getMail()) != 0) {
                    instance.SendGCMMessageAsync(user.getMail(), json);
                }

            }

        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    RemoveFromForum(forum);
                }
            });

        }


    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration ID and app versionCode in the application's
     * shared preferences.
     */
    void RegisterAsync(final Callback<Void> onEnd) {

        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                try {

                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    Log.i(getClass().toString(), gcm.toString());
                    regId = gcm.register(SENDER_ID);
                    Log.i(getClass().toString(), "Device registered, registration ID=" + regId);

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    SendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the registration ID - no need to register again.
                    prefs.StoreRegistrationId(regId);

                } catch (IOException ex) {

                    Log.e(getClass().toString(), ex.getMessage());
                    return false;
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.

                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean success) {

                if(onEnd != null) {
                    onEnd.Invoke(null);
                }

            }

        }.execute();

    }

    /**
     * Sends the registration ID to your server over HTTP, so it can use
     * GCM/HTTP or CCS to send messages to your app. Not needed for this demo
     * since the device sends upstream messages to a server that echoes back the
     * message using the 'from' address in the message.
     */
    void SendRegistrationIdToBackend() {
        try {
            com.aroundme.deviceinfoendpoint.Deviceinfoendpoint endpoint = EndpointApiCreator
                    .getApi(Deviceinfoendpoint.class);
            DeviceInfo existingInfo = endpoint.getDeviceInfo(regId).execute();

            boolean alreadyRegisteredWithEndpointServer = false;
            if (existingInfo != null
                    && regId.equals(existingInfo.getDeviceRegistrationID())) {
                alreadyRegisteredWithEndpointServer = true;
            }

            if (!alreadyRegisteredWithEndpointServer) {
				/*
				 * We are not registered as yet. Send an endpoint message
				 * containing the GCM registration id and some of the device's
				 * product information over to the backend. Then, we'll be
				 * registered.
				 */
                DeviceInfo deviceInfo = new DeviceInfo();
                endpoint.insertDeviceInfo(
                        deviceInfo.setDeviceRegistrationID(regId)
                                .setTimestamp(System.currentTimeMillis())
                                .setDeviceInformation(URLEncoder.encode(android.os.Build.MANUFACTURER + " " + android.os.Build.PRODUCT, "UTF-8"))).execute();
            }
        } catch (Exception e) {
            Log.e(getClass().toString(), e.getMessage());
        }

    }

    void RegisterUserAsync(final User user, final Callback<User> onEnd) {

        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {

                try {
                    Log.i(TAG, user.toString());
                    User u = api.register(user).execute();
                    if(onEnd != null) {
                        onEnd.Invoke(u);
                    } else {
                        Log.e(TAG, "Callback in RegisterUserAsync is null");
                    }
                } catch (IOException e) {
                    Log.e(getClass().toString() + ":registerAsync:",  e.getMessage());
                    if(onEnd != null){
                        onEnd.Invoke(null);
                    } else {
                        Log.e(TAG, "Callback in RegisterUserAsync is null");
                    }
                }

                return false;

            }

        }.execute();

    }

    void LoginAsync(final User user, final Callback<User> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {

                    Log.i(TAG, "User login comencing");

                    User u = api.login(user.getMail(), user.getPassword(), user.getRegistrationId()).execute();

                    Log.i(TAG, "User login returned");
                    if(onEnd != null) {
                        onEnd.Invoke(u);
                    } else {
                        Log.e(TAG, "Callback in LoginAsync is null");
                    }
                }catch (Exception e) {
                    Log.e(getClass().toString() + ":LoginAsync:", e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(null);
                    } else {
                        Log.e(TAG, "Callback in LoginAsync is null");
                    }
                }
                return null;

            }

        }.execute();

    }

    void SendMessageAsync(final String message, final Callback<Boolean> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                Message newMessage = new Message();
                newMessage.setContnet(message);
                try {
                    api.sendMessage(newMessage).execute();
                    if(onEnd != null) {
                        onEnd.Invoke(true);
                    }
                } catch(IOException e) {
                    Log.e(getClass().toString(), e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(false);
                    }
                }

                return null;

            }

        }.execute();

    }

    void ReportLocationAsync(final GeoPt location, final Callback<Boolean> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {
                    api.reportUserLocation(user.getMail(), location).execute();
                    if(onEnd != null) {
                        onEnd.Invoke(true);
                    }
                } catch(IOException e) {
                    Log.e(getClass().toString(), "Error reporting user location: " + e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(false);
                    }
                }

                return null;

            }

        }.execute();

    }

    void SendStructuredMessageAsync(final Message message, final Callback<Boolean> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {
                    api.sendMessage(message).execute();
                    if(onEnd != null) {
                        onEnd.Invoke(true);
                    }
                } catch(IOException e) {
                    Log.e(getClass().toString(), e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(false);
                    }
                }

                return null;
            }
        }.execute();

    }

    void GetUsersAroundMeAsync(final GeoPt point, final int radius, final String mail, final Callback<List<UserAroundMe>> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {
                    List<UserAroundMe> retVal = api.getUsersAroundMe(point.getLatitude(), point.getLongitude(), radius, mail).execute().getItems();
                    if(onEnd != null) {
                        onEnd.Invoke(retVal);
                    }
                } catch(IOException e) {
                    Log.e(getClass().toString(), "Failed to get users around me .... fucking shit: " + e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(null);
                    }
                }
                return null;

            }
        }.execute();

    }

    void GetUsersAroundPoint(final int radius, final float lat, final float lon, final Callback<List<UserAroundMe>> callback) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {

                    List<UserAroundMe> retVal = api.getUsersAroundMe(lat, lon, radius, user.getMail()).execute().getItems();
                    if(callback!= null) {
                        callback.Invoke(retVal);
                    }

                } catch(IOException e) {
                    Log.e(getClass().toString(), "Failed to get users around me .... fucking shit: " + e.getMessage());
                    if(callback != null) {
                        callback.Invoke(null);
                    }
                }
                return null;

            }
        }.execute();

    }

    void SendForumMessage(final LocoForum forum, final String message, final Callback<Boolean> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                String sentMessage = "{ 'type': 'forumMessage', "
                        + "'message': "
                            + " 'from': '" + user.getMail() + "'"
                            + " 'time': " + new DateTime(new Date()).getValue()
                            + " 'content': '" + message + "' }";

                try {
                    for (LocoUser u : forum.getUsers()){
                        api.sendGcmMessage(u.getMail(), sentMessage);
                    }
                    if(onEnd != null) {
                        onEnd.Invoke(true);
                    }
                } catch (IOException e) {
                    Log.e(getClass().toString(), "Could not send forum message");
                    if(onEnd != null) {
                        onEnd.Invoke(false);
                    }
                }

                return null;
            }
        }.execute();

    }

    void RetrieveMessageAsync(final long messageId, final Callback<Message> onEnd) {

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                try {
                    Message m = api.getMessage(messageId).execute();
                    if(onEnd != null) {
                        onEnd.Invoke(m);
                    }
                } catch (IOException e) {
                    Log.e(getClass().toString(), e.getMessage());
                    if(onEnd != null) {
                        onEnd.Invoke(null);
                    }
                }
                return null;
            }

        }.execute();

    }

    void SendGCMMessageAsync(final String mail, final String message) {

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    api.sendGcmMessage(mail, message).execute();
                } catch (IOException e) {
                    Log.e(getClass().toString(), e.getMessage());
                }
                return null;
            }
        }.execute();

    }

    public static void GetAllUsers(final String mail, final Callback<List<UserAroundMe>> callback) {

        if(instance.initialized) {

            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {

                    try {

                        List<UserAroundMe> result = instance.api.getAllUsers(mail).execute().getItems();
                        callback.Invoke(result);

                    } catch (IOException e) {
                        Log.e(TAG, "Could not retrieve users");
                    }

                    return null;
                }

            }.execute();

        } else {

            instance.delayedCalls.add(new Callback<Void>() {
                @Override
                public void Invoke(Void result) {
                    GetAllUsers(mail, callback);
                }
            });

        }

    }

}
