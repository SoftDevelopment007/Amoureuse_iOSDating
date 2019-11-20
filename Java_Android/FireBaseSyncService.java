package com.forumrelated.forumrelated.services;

import android.graphics.Bitmap;
import android.location.Location;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.forumrelated.forumrelated.app.App;
import com.forumrelated.forumrelated.common.RemoteConfig;
import com.forumrelated.forumrelated.common.Utils;
import com.forumrelated.forumrelated.event.DeleteImageGrid;
import com.forumrelated.forumrelated.event.DisLikeUserEvent;
import com.forumrelated.forumrelated.event.EndFireBaseSync;
import com.forumrelated.forumrelated.event.EndProfileUpdateToFirebaseBase;
import com.forumrelated.forumrelated.event.GPSSyncEvent;
import com.forumrelated.forumrelated.event.GridImagesUpdatedEvent;
import com.forumrelated.forumrelated.event.LikeUserEvent;
import com.forumrelated.forumrelated.event.MessageAddedEvent;
import com.forumrelated.forumrelated.event.ReportUserEvent;
import com.forumrelated.forumrelated.event.StartFireBaseSync;
import com.forumrelated.forumrelated.event.StartImageUpload;
import com.forumrelated.forumrelated.event.StartProfileUpdateToFireBase;
import com.forumrelated.forumrelated.event.StartUpdateGridImages;
import com.forumrelated.forumrelated.helper.NotificationHelper;
import com.forumrelated.forumrelated.helper.PreferencesHelper;
import com.forumrelated.forumrelated.model.LikeDislikeModel;
import com.forumrelated.forumrelated.model.MatchSetting;
import com.forumrelated.forumrelated.model.UserReportModel;
import com.forumrelated.forumrelated.model.user.GridImages;
import com.forumrelated.forumrelated.model.user.PrivateProfile;
import com.forumrelated.forumrelated.model.user.PublicProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;
import de.greenrobot.event.ThreadMode;

/**
 * Created by sabeeh on 16-8-23.
 */

public class FireBaseSyncService {
    private EventBus eventBus;
    private FirebaseUser currentUser;
    private DatabaseReference userProfileRef;
    private DatabaseReference mProfilesRef;
    private DatabaseReference mLocationRef;
    private DatabaseReference mGridImageRef;
    private StorageReference mStorageRef;
    private DatabaseReference mSettingRef;
    private DatabaseReference mLikesChannelRef;
    /*private DatabaseReference mLikesRef;
    private DatabaseReference mDisLikesRef;*/
    private DatabaseReference mReportUserRef;

    private String thumbChildString;

    private static final int REQUEST_FINE_LOCATION = 0;
    private String TAG = FireBaseSyncService.class.getSimpleName();
    private boolean isInitialized = false;
    private DatabaseReference mProfilesBlock;

    public FireBaseSyncService(EventBus bus) {
        if (!FirebaseApp.getApps(App.getInstance().getApplicationContext()).isEmpty()) {
            initFirebase();
        }
        eventBus = bus;
    }

    public void initFirebase() {
        if (!isInitialized) {
            FirebaseDatabase.getInstance().setPersistenceEnabled(Utils.isNetworkAvailable() && PreferencesHelper.isCacheNeededToRefresh());
            isInitialized = true;
            mLikesChannelRef = FirebaseDatabase.getInstance().getReference().child("channelLikes");
            mReportUserRef = FirebaseDatabase.getInstance().getReference().child("reports");
            mProfilesRef = FirebaseDatabase.getInstance().getReference().child("profiles");
            mStorageRef = FirebaseStorage.getInstance().getReference();
            mProfilesBlock = FirebaseDatabase.getInstance().getReference().child("profilesBlock");
        }
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void handle(StartFireBaseSync event) {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        mLikesChannelRef.child(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                handleAllLikeDislikes(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled: " + databaseError.getMessage());
            }
        });

        mReportUserRef.child(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: mReportUserRef");
                handleReportDataSnapshots(dataSnapshot);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled: " + databaseError.getMessage());
            }
        });
        userProfileRef = FirebaseDatabase.getInstance().getReference().child("profiles").child(currentUser.getUid());
        mGridImageRef = FirebaseDatabase.getInstance().getReference().child("gridPictures").child(currentUser.getUid());
        loadFromFireBase(event);
        loadSettingFromFireBase();

//        listener for where this user is liked
        /*mLikesRef.orderByChild("potentialId").equalTo(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: mLikesRef");
                mLikesChannelRef.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        handleLikesDislikesDataSnapshots(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

//                listener for where this user is disliked
        mDisLikesRef.orderByChild("potentialId").equalTo(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: mDisLikesRef");
                mLikesChannelRef.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        handleLikesDislikesDataSnapshots(dataSnapshot);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });*/
    }

    private void handleAllLikeDislikes(DataSnapshot snapshots) {
        if (snapshots.getValue() != null) {
            Map<String, Boolean> likedDisLikedUsers = (Map<String, Boolean>) snapshots.getValue();
            if (likedDisLikedUsers != null) {
                App.getInstance().setLikedDisLikedUsers(likedDisLikedUsers);
            } else {
                App.getInstance().setLikedDisLikedUsers(new HashMap<String, Boolean>());
            }
//            handleLikesDislikesDataSnapshots(snapshots.child(currentUser.getUid()));
        } else {
            App.getInstance().setLikedDisLikedUsers(new HashMap<String, Boolean>());
        }

        Map<String, Boolean> likedDislikedUsers = App.getInstance().getLikedDisLikedUsers();
        for (final Map.Entry<String, Boolean> entry : likedDislikedUsers.entrySet()) {
            // TODO: test it
            mLikesChannelRef.child(entry.getKey()).child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    boolean didUserLiked = entry.getValue();
                    boolean isUserLiked = false;

                    if (dataSnapshot.getValue() != null && (boolean) dataSnapshot.getValue()) {
                        isUserLiked = true;
                    }

                    App.getInstance().addTwoSideLikedUsers(entry.getKey(), didUserLiked && isUserLiked);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d(TAG, "onCancelled: " + databaseError.getMessage());
                }
            });
        }
    }

    private void handleReportDataSnapshots(DataSnapshot dataSnapshot) {
        Map<String, UserReportModel> reportedUsers = (Map<String, UserReportModel>) dataSnapshot.getValue();
        if (reportedUsers != null) {
            App.getInstance().setReportedUsers(reportedUsers);
        } else {
            App.getInstance().setReportedUsers(new HashMap<String, UserReportModel>());
        }
    }

    private void handleLikesDislikesDataSnapshots(DataSnapshot dataSnapshot) {
        Map<String, Boolean> likedDisLikedUsers = (Map<String, Boolean>) dataSnapshot.getValue();
        if (likedDisLikedUsers != null) {
            App.getInstance().setLikedDisLikedUsers(likedDisLikedUsers);
            for (final Map.Entry<String, Boolean> entry : likedDisLikedUsers.entrySet()) {
                mLikesChannelRef.child(entry.getKey()).child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        boolean didUserLiked = App.getInstance().isUserLiked(entry.getKey());
                        Map<String, Boolean> twoSideLikedUsers = App.getInstance().getTwoSideLikedUsers();
                        if (dataSnapshot != null && dataSnapshot.getValue() != null) {
                            boolean isUserLiked = (boolean) dataSnapshot.getValue();
                            Log.d(TAG, "onDataChange: key: " + entry.getKey() + ", didLiked: " + String.valueOf(didUserLiked) + ", isLiked: " + String.valueOf(isUserLiked));
                            twoSideLikedUsers.put(entry.getKey(), didUserLiked && isUserLiked);
                            App.getInstance().setTwoSideLikedUsers(twoSideLikedUsers);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.d(TAG, "onCancelled: " + databaseError.getMessage());
                    }
                });
            }
        } else {
            App.getInstance().setLikedDisLikedUsers(new HashMap<String, Boolean>());
            App.getInstance().setTwoSideLikedUsers(new HashMap<String, Boolean>());
        }
    }

    private void loadSettingFromFireBase() {
        mSettingRef = FirebaseDatabase.getInstance().getReference().child("searchingUsers").child(currentUser.getUid());

        mSettingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                MatchSetting matchSetting = dataSnapshot.getValue(MatchSetting.class);
                if (matchSetting == null) {
                    MatchSetting setting = new MatchSetting((int) RemoteConfig.getMinAge(), (int) RemoteConfig.getMaxAge(), (int) RemoteConfig.getMaxDistance(), 3);
                    mSettingRef.setValue(setting);
                } else {
                    App.getInstance().setMatchSetting(matchSetting);
//                    eventBus.post(new EndFireBaseSync());
//
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("user", "The read failed" + databaseError.getDetails());
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void syncLocation(GPSSyncEvent event) {
        Log.d(TAG, "syncLocation: ");
        mLocationRef = FirebaseDatabase.getInstance().getReference().child("locations");
        final GeoFire geoFire = new GeoFire(mLocationRef);

        Location lastLocation = App.getInstance().getGoogleApiHelper().getLastLocation();
        if (lastLocation != null) {
            if (currentUser != null) {
                Log.d(TAG, "syncLocation: setLocation: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                geoFire.setLocation(currentUser.getUid(), new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));
            }
            if (App.getInstance() != null) {
                App.getInstance().setCurrentLocation(lastLocation.getLatitude(), lastLocation.getLongitude());
            }
            if (currentUser == null) {
                currentUser = FirebaseAuth.getInstance().getCurrentUser();
            }

            if (currentUser != null) {
                geoFire.setLocation(currentUser.getUid(), new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));
            }
            if (App.getInstance() != null) {
                App.getInstance().setCurrentLocation(lastLocation.getLatitude(), lastLocation.getLongitude());
            }
        }

        /*
        geoFire.setLocation(currentUser.getUid().toString(), new GeoLocation(30.006605,69.8223813));
        App.getInstance().setCurrentLocation(30.006605,69.8223813);*/
        /*
        SmartLocation.with(App.getInstance().getApplicationContext())
                .location()
                .oneFix()
                .start(new OnLocationUpdatedListener() {
                    @Override
                    public void onLocationUpdated(Location location) {
                        if (location != null) {
                            Log.d(TAG, "onLocationUpdated: ");
                            if (currentUser != null) {
                                geoFire.setLocation(currentUser.getUid(), new GeoLocation(location.getLatitude(), location.getLongitude()));
                            }
                            if (App.getInstance() != null) {
                                App.getInstance().setCurrentLocation(location.getLatitude(), location.getLongitude());
                            }
                        }
                    }
                });*/
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void userProfileUpdate(StartProfileUpdateToFireBase event) {

        userProfileRef.child("public").updateChildren(App.getInstance().getPublicProfile().getUpdateMap()).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
            }
        });
        userProfileRef.child("private").updateChildren(App.getInstance().getPrivateProfile().getUpdateMap()).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
            }
        });

        eventBus.post(new EndProfileUpdateToFirebaseBase());
    }

    private void loadFromFireBase(final StartFireBaseSync event) {
        String gender = "";
        final String dob = event.getBirthday();

        if (!TextUtils.isEmpty(event.getGender())) {
            gender = event.getGender().toLowerCase();
            gender = event.getGender().substring(0, 1).toUpperCase() + gender.substring(1);
        }

        final String genderVal = gender;
        userProfileRef.child("public").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                PublicProfile publicProfile = dataSnapshot.getValue(PublicProfile.class);
                if (publicProfile == null) {
                    publicProfile = new PublicProfile(currentUser.getDisplayName(), null, genderVal, event.getAbout(), null);
                    publicProfile.setCoverImageStandard(event.getCover());
                    publicProfile.setCoverImageThumb(event.getCover());
                    publicProfile.setYearOfBirth(event.getYearOfBirth());
                    publicProfile.setGender(genderVal);

                    if (currentUser.getPhotoUrl() != null) {
                        publicProfile.setAvatarStandard(currentUser.getPhotoUrl().toString());
                        publicProfile.setAvatarThumb(currentUser.getPhotoUrl().toString());
                    }
                    userProfileRef.child("public").setValue(publicProfile.getInsertMap()).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "onFailure: " + e.getMessage());
                        }
                    });

                    App.getInstance().setPublicProfile(publicProfile);
                } else {
                    App.getInstance().setPublicProfile(publicProfile);
                    mProfilesBlock.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            boolean isBlocked = false;
                            if (dataSnapshot != null) {
                                isBlocked = dataSnapshot.hasChild(currentUser.getUid());
                            }
                            eventBus.post(new EndFireBaseSync(isBlocked));
                            /*if (dataSnapshot !    = null) {
                                Log.d(TAG, "mProfilesBlock: ref: " + mProfilesBlock.child(currentUser.getUid()).getRef());
                                Log.d(TAG, "mProfilesBlock: key: " + dataSnapshot.getKey());
                                Log.d(TAG, "mProfilesBlock: value: " + dataSnapshot.getValue());
                            }
                            eventBus.post(new EndFireBaseSync(dataSnapshot != null && dataSnapshot.getValue() != null));*/
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.d(TAG, "onCancelled: ");
                            eventBus.post(new EndFireBaseSync(false));
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("user", "The read failed" + databaseError.getDetails());
            }
        });

        userProfileRef.child("private").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                PrivateProfile privateProfile = dataSnapshot.getValue(PrivateProfile.class);
                if (privateProfile == null) {
                    PrivateProfile mPrivateProfile = new PrivateProfile();

                    if (!TextUtils.isEmpty(dob)) {
                        mPrivateProfile.setDob(dob);
                    }

                    userProfileRef.child("private").setValue(mPrivateProfile.getInsertMap()).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "onFailure: " + e.getMessage());
                        }
                    });
                    App.getInstance().setPrivateProfile(mPrivateProfile);
                } else {
                    App.getInstance().setPrivateProfile(privateProfile);
                    mProfilesBlock.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            boolean isBlocked = false;
                            if (dataSnapshot != null) {
                                isBlocked = dataSnapshot.hasChild(currentUser.getUid());
                            }
                            eventBus.post(new EndFireBaseSync(isBlocked));
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.d(TAG, "onCancelled: ");
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("user", "The read failed" + databaseError.getDetails());
            }
        });

        mGridImageRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GridImages gridImages = dataSnapshot.getValue(GridImages.class);
                if (gridImages != null) {
                    App.getInstance().setGridImages(gridImages);
                } else {
                    App.getInstance().setGridImages(new GridImages());
                }
                eventBus.post(new GridImagesUpdatedEvent());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled: " + databaseError.getMessage());
            }
        });


//        userProfileRef.setValue(ServerValue.TIMESTAMP);
    }


    // [START upload_from_uri]
    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void imageUpload(final StartImageUpload event) {
        final StorageReference photoRef;
        String uid = currentUser.getUid();
        if (event.imageType == 1) {
            photoRef = mStorageRef.child("/avatars").child(uid).child("/avatar_standard.jpg");
        } else if (event.imageType == 2) {
            photoRef = mStorageRef.child("/covers").child(uid).child("/cover_image_standard.jpg");
        } else {
            long unixTime = System.currentTimeMillis() / 1000L;
            String childString = "/" + String.valueOf(unixTime) + "_standard.jpg";
            thumbChildString = "/" + String.valueOf(unixTime) + "_thumb.jpg";
            photoRef = mStorageRef.child("/gridPictures").child(uid).child(childString);
        }

        photoRef.putFile(event.fileUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Upload succeeded
//                        Log.d(TAG, "uploadFromUri:onSuccess");

                        // Get the public download URL
                        Uri mMainDownloadUrl = taskSnapshot.getMetadata().getDownloadUrl();
                        uploadThumbImage(event, mMainDownloadUrl);

                        // [START_EXCLUDE]
//                        hideProgressDialog();
//                        updateUI(mAuth.getCurrentUser());
                        // [END_EXCLUDE]
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        // Upload failed
//                        Log.w(TAG, "uploadFromUri:onFailure", exception);

                        Uri mDownloadUrl = null;
//                        eventBus.post(new GridImagesUpdatedEvent());
                        // [START_EXCLUDE]
//                        hideProgressDialog();
//                        updateUI(mAuth.getCurrentUser());
                        // [END_EXCLUDE]
                    }
                });
    }

    private void uploadThumbImage(final StartImageUpload event, final Uri standardDownloadUrl) {
        Bitmap bitmap1 = null;
        final StorageReference photoRef;
        try {
            bitmap1 = MediaStore.Images.Media.getBitmap(App.getInstance().getContentResolver(), event.fileUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = null;
        String uid = currentUser.getUid();
        if (event.imageType == 1) {
            bitmap = ThumbnailUtils.extractThumbnail(bitmap1, 60, 60);
            photoRef = mStorageRef.child("/avatars").child(uid).child("/avatar_thumb.jpg");
        } else if (event.imageType == 2) {
            bitmap = ThumbnailUtils.extractThumbnail(bitmap1, 600, 240);
            photoRef = mStorageRef.child("/covers").child(uid).child("/cover_image_thumb.jpg");
        } else {
            bitmap = ThumbnailUtils.extractThumbnail(bitmap1, 120, 120);
            photoRef = mStorageRef.child("/gridPictures").child(uid).child(thumbChildString);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data1 = baos.toByteArray();


//        photoRef.getName().equals(photoRef.getName());
        UploadTask uploadTask = photoRef.putBytes(data1);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.d(TAG, "onFailure: " + exception.getMessage());
                // Handle unsuccessful uploads
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                Uri subDownloadUrl = taskSnapshot.getDownloadUrl();

                //eventBus.post(new GridImagesUpdatedEvent(false,subDownloadUrl));
                if (event.imageType == 1) {
                    App.getInstance().getPublicProfile().setAvatarStandard(standardDownloadUrl.toString());
                    App.getInstance().getPublicProfile().setAvatarThumb(subDownloadUrl.toString());
                    userProfileUpdate(new StartProfileUpdateToFireBase());

                } else if (event.imageType == 2) {
                    App.getInstance().getPublicProfile().setCoverImageStandard(standardDownloadUrl.toString());
                    App.getInstance().getPublicProfile().setCoverImageThumb(subDownloadUrl.toString());
                    userProfileUpdate(new StartProfileUpdateToFireBase());
                } else {
                    App.getInstance().getGridImages().addNewImage(standardDownloadUrl.toString(), subDownloadUrl.toString());
                    mGridImageRef.setValue(App.getInstance().getGridImages());
                }
                eventBus.post(new GridImagesUpdatedEvent());

            }
        });

    }


    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void deleteGridImage(DeleteImageGrid event) {
        GridImages images = App.getInstance().getGridImages();
        /*for (String image : event.getSelectedItems()) {
            if (!TextUtils.isEmpty(image)) {
                images.removeThumb(image);
            }
        }*/
        Integer[] selectedIndices = event.getSelectedIndices();
        StorageReference photoRef = mStorageRef.child("/gridPictures").child(currentUser.getUid());
        int offset = 0;
        for (int i = 0; i < selectedIndices.length; i++) {
            int index = i - offset;
            String standard = images.getStandard(selectedIndices[index]);
            if (!TextUtils.isEmpty(standard)) {
                photoRef.getStorage().getReferenceFromUrl(standard).delete();
            }
            String thumb = images.getThumb(selectedIndices[index]);
            if (!TextUtils.isEmpty(thumb)) {
                photoRef.getStorage().getReferenceFromUrl(thumb).delete();
            }
            images.removeThumb(selectedIndices[index]);
            ++offset;
        }
        mGridImageRef.setValue(images);
        App.getInstance().setGridImages(images);
        eventBus.post(new GridImagesUpdatedEvent());
    }


    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void updateGridImages(StartUpdateGridImages event) {
        mGridImageRef.setValue(App.getInstance().getGridImages());
        eventBus.post(new GridImagesUpdatedEvent());
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void likeUser(final LikeUserEvent event) {
        Map<String, Boolean> users = App.getInstance().getLikedDisLikedUsers();
        final String potentialUserUid = event.getPotentialUserUid();
        final String currentUserUid = currentUser.getUid();

        users.put(potentialUserUid, true);
        App.getInstance().setLikedDisLikedUsers(users);

        likeOrDislike(currentUserUid, potentialUserUid, true);
        LikeDislikeModel likeModel = new LikeDislikeModel(potentialUserUid, currentUserUid);
//        DatabaseReference pushNode = mLikesRef.push();
//        pushNode.setValue(likeModel);

//        check if user also liked then send push notifications
        mLikesChannelRef.child(potentialUserUid).child(currentUserUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot != null && dataSnapshot.getValue() != null && ((boolean) dataSnapshot.getValue())) {
                    String message = event.getPotentialUserName();
                    NotificationHelper.sendMessageByTopic(currentUserUid, "New Match Added", message, "", potentialUserUid);
                    message = App.getInstance().getPublicProfile().getName();
                    NotificationHelper.sendMessageByTopic(potentialUserUid, "New Match Added", message, "", currentUserUid);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "onCancelled: " + databaseError);
            }
        });

    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void disLikeUser(DisLikeUserEvent event) {
        Map<String, Boolean> users = App.getInstance().getLikedDisLikedUsers();
        String potentialUserUid = event.getPotentialUserUid();
        String currentUserUid = currentUser.getUid();

        users.put(potentialUserUid, false);
        App.getInstance().setLikedDisLikedUsers(users);

        likeOrDislike(currentUserUid, potentialUserUid, false);

//        LikeDislikeModel dislikeModel = new LikeDislikeModel(potentialUserUid, currentUserUid);
//        DatabaseReference pushNode = mDisLikesRef.push();
//        pushNode.setValue(dislikeModel);
    }

    private void likeOrDislike(String currentUserUid, String potentialUserUid, boolean like) {
        mLikesChannelRef.child(currentUserUid).child(potentialUserUid).setValue(like);
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void onChatMessageAdded(MessageAddedEvent event) {
        Log.d(TAG, "onChatMessageAdded: userRef: " + event.getUserRef() + ", potentialUser: " + event.getPotentialUserRef() + ", msgKey: " + event.getMessageKey());
    }

    @Subscribe(threadMode = ThreadMode.BackgroundThread)
    public void reportUser(ReportUserEvent event) {
        String potentialUserId = event.getPotentialUserId();
        if (TextUtils.isEmpty(potentialUserId)) {
            return;
        }

        Map<String, UserReportModel> reportedUser = App.getInstance().getReportedUsers();

//        remove chat of both on block
        String currentUserUid = currentUser.getUid();
        App.getConversationsRef().child(potentialUserId).child(currentUserUid).removeValue();
        App.getConversationsRef().child(currentUserUid).child(potentialUserId).removeValue();

        disLikeUser(new DisLikeUserEvent(potentialUserId));
        likeOrDislike(potentialUserId, currentUserUid, false);

        /*mProfilesBlock.child(currentUserUid).child(potentialUserId).setValue(true).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
            }
        });*/

        UserReportModel reportModel = event.getUserReportModel();
        if (reportModel != null) {
            reportedUser.put(potentialUserId, reportModel);
            mReportUserRef.child(currentUserUid).child(potentialUserId).setValue(reportModel.getInsertMap()).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "onFailure: " + e.getMessage());
                }
            });
        }
    }

    public DatabaseReference getGridImageRef() {
        return mGridImageRef;
    }

    public DatabaseReference getUserProfileRef() {
        return userProfileRef;
    }

    public DatabaseReference getProfilesRef() {
        return mProfilesRef;
    }

    public DatabaseReference getLikesRef() {
        return mLikesChannelRef;
    }

    public FirebaseUser getCurrentUser() {
        return currentUser;
    }

    public boolean isInitialized() {
        return isInitialized;
    }
}
