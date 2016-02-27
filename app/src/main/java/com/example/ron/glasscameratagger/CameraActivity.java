package com.example.ron.glasscameratagger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;
import com.clarifai.api.exception.ClarifaiException;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link Activity} showing a tuggable "Hello World!" card.
 * <p/>
 * The main content view is composed of a one-card {@link CardScrollView} that provides tugging
 * feedback to the user when swipe gestures are detected.
 * If your Glassware intends to intercept swipe gestures, you should set the content view directly
 * and use a {@link com.google.android.glass.touchpad.GestureDetector}.
 *
 * @see <a href="https://developers.google.com/glass/develop/gdk/touch">GDK Developer Guide</a>
 */
public class CameraActivity extends Activity /*implements View.OnClickListener*/ {

    private static final String TAG = "CameraActivity";

    private boolean mSafeToTakePicture;
    private List<CardBuilder> cards;
    private List<String> cardText;
    private LinearLayout frame_layout;
    private SurfaceView surface_view;
    private CardScrollView scroll_view;
    private ClarifaiCardScrollAdapter sv_adapter;
    private Camera mCamera;
    private GestureDetector mGestureDetector;
    SurfaceHolder surface_holder = null;
    SurfaceHolder.Callback sh_callback = null;
    private String selectedTerm = null;

    private final ClarifaiClient client = new ClarifaiClient(Credentials.CLIENT_ID,
            Credentials.CLIENT_SECRET);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        mSafeToTakePicture = false;

        frame_layout = new LinearLayout(getApplicationContext());

        surface_view = new SurfaceView(getApplicationContext());
        surface_view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (surface_holder == null) {
            surface_holder = surface_view.getHolder();
        }

        sh_callback = my_callback();
        surface_holder.addCallback(sh_callback);

        cards = new ArrayList<>();
        cardText = new ArrayList<>();

        scroll_view = new CardScrollView(getApplicationContext());
        scroll_view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                120));
        sv_adapter = new ClarifaiCardScrollAdapter();
        scroll_view.setAdapter(sv_adapter);
        scroll_view.activate();
        scroll_view.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedTerm = cardText.get(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                selectedTerm = null;
            }
        });

        FrameLayout.LayoutParams params1 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        FrameLayout.LayoutParams params2 = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 80);

        params1.gravity = Gravity.CENTER_VERTICAL;

        frame_layout.setOrientation(LinearLayout.VERTICAL);
        frame_layout.addView(scroll_view, params2);
        frame_layout.addView(surface_view, params1);

        addContentView(frame_layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mGestureDetector = createGestureDetector(getApplicationContext());
    }

    /*
     * Send generic motion events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    SurfaceHolder.Callback my_callback() {
        return new SurfaceHolder.Callback() {

            private byte[] cb_buffer = new byte[1048576];

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSafeToTakePicture = false;
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mCamera = Camera.open();

                try {
                    mCamera.setPreviewDisplay(holder);
                } catch (IOException exception) {
                    mCamera.release();
                    mCamera = null;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                       int height) {
                mCamera.startPreview();
                mSafeToTakePicture = true;
            }
        };
    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        //Create a base listener for generic gestures
        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    // do something on tap
                    Log.v(TAG, "tap");
                    //if (readyForMenu) {
                    //openOptionsMenu();
                    //}
                    if (mSafeToTakePicture) {
                        mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
                    } else {
                        Log.w(TAG, "camera preview not set up yet");
                    }
                    return true;
                } else if (gesture == Gesture.TWO_TAP) {
                    // do something on two finger tap
                    return true;
                } else if (gesture == Gesture.SWIPE_RIGHT) {
                    // do something on right (forward) swipe
                    return true;
                } else if (gesture == Gesture.SWIPE_LEFT) {
                    // do something on left (backwards) swipe
                    return true;
                } else if (gesture == Gesture.LONG_PRESS) {
                    // do something on long press
                    if (selectedTerm != null) {
                        Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "http://www.google.com/search?q=" + selectedTerm
                        ));
                        startActivity(browse);
                    }
                }
                return false;
            }
        });
        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
            @Override
            public void onFingerCountChanged(int previousCount, int currentCount) {
                // do something on finger count changes
            }
        });
        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
            @Override
            public boolean onScroll(float displacement, float delta, float velocity) {
                // do something on scrolling
                return false;
            }
        });
        return gestureDetector;
    }

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            // TODO Auto-generated method stub
            Toast.makeText(getApplicationContext(),
                    "Image has been captured..", Toast.LENGTH_SHORT).show();

        }
    };
    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
        }
    };
    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            if (data != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                new AsyncTask<Bitmap, Void, RecognitionResult>() {
                    @Override
                    protected RecognitionResult doInBackground(Bitmap... bitmaps) {
                        return recognizeBitmap(bitmaps[0]);
                    }

                    @Override
                    protected void onPostExecute(RecognitionResult result) {
                        updateUIForResult(result);
                    }
                }.execute(bmp);
            } else {
                Log.w(TAG, "did not receive picture");
            }
        }
    };

    /** Sends the given bitmap to Clarifai for recognition and returns the result. */
    private RecognitionResult recognizeBitmap(Bitmap bitmap) {
        try {
            // Scale down the image. This step is optional. However, sending large images over the
            // network is slow and  does not significantly improve recognition performance.
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 320,
                    320 * bitmap.getHeight() / bitmap.getWidth(), true);

            // Compress the image as a JPEG.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            byte[] jpeg = out.toByteArray();

            // Send the JPEG to Clarifai and return the result.
            return client.recognize(new RecognitionRequest(jpeg)).get(0);
        } catch (ClarifaiException e) {
            Log.e(TAG, "Clarifai error", e);
            return null;
        }
    }

    /** Updates the UI by displaying tags for the given result. */
    private void updateUIForResult(RecognitionResult result) {
        if (result != null) {
            if (result.getStatusCode() == RecognitionResult.StatusCode.OK) {
                // Display the list of tags in the UI.
                cards.clear();
                cardText.clear();
                for (Tag tag : result.getTags()) {
                    cards.add(new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT)
                            .setText("") /* <-- this will be clipped off the screen */
                            .setFootnote(tag.getName() + "   (swing arm to shop)"));
                    cardText.add(tag.getName());
                    Log.d(TAG, tag.getName());
                }
                sv_adapter.notifyDataSetChanged();
            } else {
                Log.e(TAG, "Clarifai: " + result.getStatusMessage());
            }
        }
    }

    private class ClarifaiCardScrollAdapter extends CardScrollAdapter {

        @Override
        public int getPosition(Object item) {
            return cards.indexOf(item);
        }

        @Override
        public int getCount() {
            return cards.size();
        }

        @Override
        public Object getItem(int position) {
            return cards.get(position);
        }

        @Override
        public int getViewTypeCount() {
            return CardBuilder.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position){
            return cards.get(position).getItemViewType();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return cards.get(position).getView(convertView, parent);
        }
    }
}
