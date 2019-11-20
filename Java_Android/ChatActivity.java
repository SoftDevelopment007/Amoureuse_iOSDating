package com.forumrelated.forumrelated.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.forumrelated.forumrelated.adapter.ChatFirebaseAdapter;
import com.forumrelated.forumrelated.app.App;
import com.forumrelated.forumrelated.helper.NotificationHelper;
import com.forumrelated.forumrelated.model.ChatModel;
import com.forumrelated.forumrelated.model.user.PublicProfile;
import com.forumrelated.forumrelated.view.WrapContentLinearLayoutManager;
import com.forumrelated.forumrelated.R;

import java.util.Date;

import hani.momanii.supernova_emoji_library.Actions.EmojIconActions;
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class ChatActivity extends AppCompatActivity implements View.OnClickListener {
    //    boolean isDataRefPathFlipped;
    public static final String EXTRA_PROFILE_UID = "profile_uid";

    //Views UI
    private RecyclerView rvListMessage;
    private WrapContentLinearLayoutManager mLinearLayoutManager;
    private ImageView btSendMessage, btEmoji;
    private EmojiconEditText edMessage;
    private View contentRoot;
    private EmojIconActions emojIcon;
    private String profileUid;
    private static PublicProfile receiverProfile;

    public static String activeUserUid;

    String userUid;
    String potentialUserUid;
    private ChatFirebaseAdapter firebaseAdapter;
    private String TAG = ChatActivity.class.getSimpleName();

    public static Intent newIntent(Context context, String profileUid, PublicProfile profile) {
        Intent intent = new Intent(context, ChatActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(ChatActivity.EXTRA_PROFILE_UID, profileUid);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        receiverProfile = profile;
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Bundle bundle = getIntent().getExtras();
        profileUid = bundle.getString(ChatActivity.EXTRA_PROFILE_UID);

        userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        potentialUserUid = profileUid;

        if (receiverProfile == null || TextUtils.isEmpty(potentialUserUid) || TextUtils.isEmpty(profileUid)) {
            finish();
            return;
        }

        /*if (userUid.compareTo(potentialUserUid) < 0) {
            potentialUserUid = userUid;
            userUid = profileUid;
            isDataRefPathFlipped = true;
        }*/

        updateActionBar();

        bindViews();
        setupChatAdapter();

        App.getInstance().getFireBaseSyncService().getProfilesRef().child(potentialUserUid).child("public").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot != null) {
                    PublicProfile publicProfile = dataSnapshot.getValue(PublicProfile.class);
                    if (publicProfile != null) {
                        receiverProfile = publicProfile;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateActionBar();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void updateActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(receiverProfile.getName());
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }


    /**
     * Vincular views com Java API
     */
    private void bindViews() {
        contentRoot = findViewById(R.id.contentRoot);
        edMessage = (EmojiconEditText) findViewById(R.id.editTextMessage);
        btSendMessage = (ImageView) findViewById(R.id.buttonMessage);
        btSendMessage.setOnClickListener(this);
        btEmoji = (ImageView) findViewById(R.id.buttonEmoji);
        emojIcon = new EmojIconActions(this, contentRoot, edMessage, btEmoji);
        emojIcon.ShowEmojIcon();
        rvListMessage = (RecyclerView) findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager = new WrapContentLinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
    }


    private void setupChatAdapter() {
        firebaseAdapter = new ChatFirebaseAdapter(
                App.getConversationsRef().child(userUid).child(potentialUserUid),
                receiverProfile, userUid, potentialUserUid
        );

        firebaseAdapter.setChatListener(new ChatFirebaseAdapter.ChatListener() {
            @Override
            public void onReceiverImageClick(int pos) {
                startActivity(FriendProfileActivity.newIntent(ChatActivity.this, potentialUserUid, receiverProfile, true));
            }
        });

        firebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                for (int i = 0; i < itemCount; i++) {
                    ChatModel chatModel = firebaseAdapter.getItem(positionStart + i);
                    if (!chatModel.isRead() && chatModel.isReply()) {
                        chatModel.setRead(true);
                        firebaseAdapter.getRef(positionStart + i).child("read").setValue(true).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d(TAG, "onFailure: " + e.getMessage());
                            }
                        });
                    }
                }
                super.onItemRangeInserted(positionStart, itemCount);
                int friendlyMessageCount = firebaseAdapter.getItemCount();
                int lastVisiblePosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (friendlyMessageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    rvListMessage.scrollToPosition(positionStart);
                }
            }
        });
        rvListMessage.setLayoutManager(mLinearLayoutManager);
        rvListMessage.setAdapter(firebaseAdapter);
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonMessage:
                sendMessageFirebase();
                break;
        }
    }

    private void sendMessageFirebase() {
        String message = edMessage.getText().toString();
        if (TextUtils.isEmpty(message)) {
            return;
        }
        ChatModel model = new ChatModel(message, false);
        DatabaseReference userChatNode = App.getConversationsRef().child(userUid).child(potentialUserUid).push();
        userChatNode.setPriority(new Date().getTime());
        userChatNode.setValue(model.getInsertMap()).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
            }
        });

        model.setReply(true);
        DatabaseReference potentialUserChatNode = App.getConversationsRef().child(potentialUserUid).child(userUid).push();
        potentialUserChatNode.setPriority(new Date().getTime());
        potentialUserChatNode.setValue(model.getInsertMap()).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " + e.getMessage());
            }
        });
        edMessage.setText(null);
        NotificationHelper.sendMessageByTopic(potentialUserUid, App.getInstance().getPublicProfile().getName(), message, "", userUid);
        updateChannelHeader();
    }

    private void updateChannelHeader() {
        final DatabaseReference userChannelHeaderRef = App.getChannelHeadersRef().child(userUid).child(potentialUserUid);
        userChannelHeaderRef.child("updatedAt").setValue(ServerValue.TIMESTAMP);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return true;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activeUserUid = "";
        if (firebaseAdapter != null) {
            firebaseAdapter.cleanup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeUserUid = profileUid;
    }
}
