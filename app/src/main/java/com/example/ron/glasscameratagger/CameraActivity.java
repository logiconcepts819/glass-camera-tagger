package com.example.ron.glasscameratagger;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.RecognitionResult;
import com.clarifai.api.Tag;
import com.clarifai.api.exception.ClarifaiException;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

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
public class CameraActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";

    private List<CardBuilder> cards;
    private LinearLayout frame_layout;
    private SurfaceView surface_view;
    private CardScrollView scroll_view;
    private ClarifaiCardScrollAdapter sv_adapter;
    private Camera mCamera;
    SurfaceHolder surface_holder = null;
    SurfaceHolder.Callback sh_callback = null;

    private final ClarifaiClient client = new ClarifaiClient(Credentials.CLIENT_ID,
            Credentials.CLIENT_SECRET);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        frame_layout = new LinearLayout(getApplicationContext());

        surface_view = new SurfaceView(getApplicationContext());
        surface_view.setOnClickListener(this);
        surface_view.setFocusable(true);
        surface_view.setFocusableInTouchMode(true);

        if (surface_holder == null) {
            surface_holder = surface_view.getHolder();
        }

        sh_callback = my_callback();
        surface_holder.addCallback(sh_callback);

        cards = new ArrayList<>();

        scroll_view = new CardScrollView(getApplicationContext());
        scroll_view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120));
        sv_adapter = new ClarifaiCardScrollAdapter();
        scroll_view.setAdapter(sv_adapter);
        scroll_view.activate();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        frame_layout.setOrientation(LinearLayout.VERTICAL);
        frame_layout.addView(surface_view, params);
        frame_layout.addView(scroll_view, params);

        addContentView(frame_layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    SurfaceHolder.Callback my_callback() {
        return new SurfaceHolder.Callback() {

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
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
            }
        };
    }

    @Override
    public void onClick(View v) {
        // perform desired action
        mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
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

            // Log.e(TAG+" onPictureTaken()", "Raw data is:------------- "
            // + data.length + " -----------");
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            new AsyncTask<Bitmap, Void, RecognitionResult>() {
                @Override protected RecognitionResult doInBackground(Bitmap... bitmaps) {
                    return recognizeBitmap(bitmaps[0]);
                }
                @Override protected void onPostExecute(RecognitionResult result) {
                    updateUIForResult(result);
                }
            }.execute(bmp);
        }
    };
    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            // TODO Auto-generated method stub
            // Log.e("JpegCallBack", new String(data));
//			JpegImage.setImageBytes(data);
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
                for (Tag tag : result.getTags()) {
                    cards.add(new CardBuilder(getApplicationContext(), CardBuilder.Layout.TEXT)
                            .setText(Html.fromHtml("<a href=\"http://www.google.com/search?q="
                            + tag.getName() + "\">" + tag.getName() + "</a>"))
                            .setFootnote("Click here to open search"));
                }
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
