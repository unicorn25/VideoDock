package cn.livall.tvbox_ui;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class VideoDockActivity extends Activity {
    private static final String TAG = "VideoDockActivity";
    
    private Gallery mGallery;
    private ImageAdapter mAdapter;
    
    @SuppressWarnings("unused")
    private static final int NUM_HOTSEATS = 5;
    private String[] mHotseatConfig = null;
    private Intent[] mHotseats = null;
    private Drawable[] mHotseatIcons = null;
    private CharSequence[] mHotseatLabels = null;    
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN , WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main);
        
        loadHotseats();
        mAdapter = new ImageAdapter(this);
        
        mGallery = (Gallery) findViewById(R.id.gallery);
        mGallery.setAdapter(mAdapter);
        mGallery.setSpacing(20);//图标间距
        mGallery.setUnselectedAlpha(2.0f);//未选择的图标透明度
        mGallery.setSelection(2);//设置第三个图标为默认选定项
        mGallery.setAnimationDuration(0);
        
//        mGallery.setCallbackDuringFling(false);
//        mGallery.setCallbackOnUnselectedItemClick(false);
        
        mGallery.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                launchHotSeat(position);
            }
        });
        
        mGallery.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAdapter.notifyDataSetChanged();
                view.requestFocus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    public void launchHotSeat(int index) {        
        if (index >= 0 && index < mHotseats.length && mHotseats[index] != null) {
            Intent intent = mHotseats[index];
            startActivitySafely(intent);
        }
    }
    
    private void loadHotseats() {
        if (mHotseatConfig == null) {
            mHotseatConfig = getResources().getStringArray(R.array.hotseats);
            if (mHotseatConfig.length > 0) {
                mHotseats = new Intent[mHotseatConfig.length];
                mHotseatLabels = new CharSequence[mHotseatConfig.length];
                mHotseatIcons = new Drawable[mHotseatConfig.length];
            } else {
                mHotseats = null;
                mHotseatIcons = null;
                mHotseatLabels = null;
            }

            TypedArray hotseatIconDrawables = getResources().obtainTypedArray(
                    R.array.hotseat_icons);
            for (int i = 0; i < mHotseatConfig.length; i++) {
                // load icon for this slot; currently unrelated to the actual
                // activity
                try {
                    mHotseatIcons[i] = hotseatIconDrawables.getDrawable(i);
                } catch (ArrayIndexOutOfBoundsException ex) {
                    Log.w(TAG, "Missing hotseat_icons array item #" + i);
                    mHotseatIcons[i] = null;
                }
            }
            hotseatIconDrawables.recycle();
        }

        PackageManager pm = getPackageManager();
        for (int i = 0; i < mHotseatConfig.length; i++) {
            Intent intent = null;
            if (mHotseatConfig[i].equals("*BROWSER*")) {
                // magic value meaning "launch user's default web browser"
                // replace it with a generic web request so we can see if there
                // is indeed a default
//                String defaultUri = getString(R.string.default_browser_url);
//                intent = new Intent(Intent.ACTION_VIEW,
//                        ((defaultUri != null) ? Uri.parse(defaultUri)
//                                : getDefaultBrowserUri()))
//                        .addCategory(Intent.CATEGORY_BROWSABLE);
                // note: if the user launches this without a default set, she
                // will always be taken to the default URL above; this is
                // unavoidable as we must specify a valid URL in order for the
                // chooser to appear, and once the user selects something, that
                // URL is unavoidably sent to the chosen app.
            } else {
                try {
                    intent = Intent.parseUri(mHotseatConfig[i], 0);
                } catch (java.net.URISyntaxException ex) {
                    Log.w(TAG, "Invalid hotseat intent: " + mHotseatConfig[i]);
                    // bogus; leave intent=null
                }
            }

            if (intent == null) {
                mHotseats[i] = null;
                mHotseatLabels[i] = getText(R.string.activity_not_found);
                continue;
            }

            ResolveInfo bestMatch = pm.resolveActivity(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            List<ResolveInfo> allMatches = pm.queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            // did this resolve to a single app, or the resolver?
            if (allMatches.size() == 0 || bestMatch == null) {
                // can't find any activity to handle this. let's leave the
                // intent as-is and let Launcher show a toast when it fails
                // to launch.
                mHotseats[i] = intent;

                // set accessibility text to "Not installed"
                mHotseatLabels[i] = getText(R.string.activity_not_found);
            } else {
                boolean found = false;
                for (ResolveInfo ri : allMatches) {
                    if (bestMatch.activityInfo.name
                            .equals(ri.activityInfo.name)
                            && bestMatch.activityInfo.applicationInfo.packageName
                                    .equals(ri.activityInfo.applicationInfo.packageName)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    // the bestMatch is probably the ResolveActivity, meaning
                    // the
                    // user has not yet selected a default
                    // so: we'll keep the original intent for now
                    mHotseats[i] = intent;

                    // set the accessibility text to "Select shortcut"
                    mHotseatLabels[i] = getText(R.string.title_select_shortcut);
                } else {
                    // we have an app!
                    // now reconstruct the intent to launch it through the front
                    // door
                    ComponentName com = new ComponentName(
                            bestMatch.activityInfo.applicationInfo.packageName,
                            bestMatch.activityInfo.name);
                    mHotseats[i] = new Intent(Intent.ACTION_MAIN)
                            .setComponent(com);

                    // load the app label for accessibility
                    mHotseatLabels[i] = bestMatch.activityInfo.loadLabel(pm);
                }
            }
        }
    }
    
    void startActivitySafely(Intent intent) {        
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {                        
            Toast.makeText(this, R.string.activity_not_found,
                    Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;        
        return super.dispatchKeyEvent(event);
    }
    
    void readShortcuts(){         
        final File configureFile = new File(Environment.getRootDirectory(), "file:///shortcutlist.xml");
        readShortcutsFromXml(configureFile);        
    }
    
    void readShortcutsFromXml(File file) {
        FileReader permReader = null;
        try {
            permReader = new FileReader(file);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Couldn't find or open configure file:" + file);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(permReader);            

            while (true) {
                if (parser.getEventType() == XmlPullParser.END_TAG) {
                    parser.next();
                    continue;
                }
                
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    permReader.close();
                    break;
                }

                String name = parser.getName();
                if ("shortcut".equals(name)) {
                    String nameStr = parser.getAttributeValue(null, "name");
                    if (nameStr != null) {
                    } else {
                    }
                    
                    String iconStr = parser.getAttributeValue(null, "icon");
                    if (iconStr != null) {
                    } else {
                    }
                } else {
                }

                parser.next();
                continue;
            }
        } catch (XmlPullParserException e) {
        } catch (IOException e) {
        }
        
    }
    


    public class ImageAdapter extends BaseAdapter {
        int mGalleryItemBackground;
        private Context mContext;
        private int mCenterWidth = 400;
        private int mCenterHeight = 260;
        
        public ImageAdapter(Context c) {
            mContext = c;
            TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
            mGalleryItemBackground = a.getResourceId(
                    R.styleable.Gallery1_android_galleryItemBackground, 0);
            a.recycle();
            
            int displayWidth = getWindowManager().getDefaultDisplay().getWidth();
            mCenterWidth = (displayWidth - 20*6)/3;
            mCenterHeight = mCenterWidth*2/3;
        }
        
        

        public int getCount() {
            return mHotseatIcons.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i;
            if (convertView == null){
                i = new ImageView(mContext);
                i.setScaleType(ImageView.ScaleType.FIT_CENTER);
                i.setBackgroundResource(mGalleryItemBackground);
            } else {
                i = (ImageView) convertView;
            }

            i.setImageDrawable(mHotseatIcons[position]);            
            i.setId(position);
            
            if (mGallery.getSelectedItemPosition() == position){
                i.setLayoutParams(new Gallery.LayoutParams(mCenterWidth, mCenterHeight));
            } else {
                i.setLayoutParams(new Gallery.LayoutParams(mCenterWidth/2, mCenterHeight/2));
            }
            
            return i;
        }
    }
    
    public void onClick(View v) {
        String pkg = "";
        String cls ="";
        switch (v.getId()) {
            case R.id.bar_icon_video:                        
                pkg = "com.tencent.qqlivehd";
                cls = "com.tencent.qqlivehd.HomeActivityGroup";
                break;
            case R.id.bar_icon_games:
                Toast.makeText(this, "this page is developing", Toast.LENGTH_LONG).show();
                pkg = "";
                cls = "";
                break; 
            case R.id.bar_icon_folder: 
                pkg = "com.softwinner.TvdFileManager";
                cls = "com.softwinner.TvdFileManager.MainUI";
                break; 
            case R.id.bar_icon_allapp:
                pkg = "cn.livall.applist";
                cls = "cn.livall.applist.Home";
                break;  
            case R.id.bar_icon_gallery: 
                pkg = "com.cooliris.media";
                cls = "com.cooliris.media.Gallery";
                break;  
            case R.id.bar_icon_browser:
                /** 原生browser */
//                pkg = "com.android.browser";
//                cls = "com.android.browser.BrowserActivity";
                /** UCweb */
                pkg = "com.UCMobile";
                cls = "com.UCMobile.main.UCMobile";
                break;  
            case R.id.bar_icon_market:
                /** 应用汇 */
//                pkg = "com.yingyonghui.market";
//                cls = "com.yingyonghui.market.ActivityMain";
                /** 机锋市场 */
                pkg = "com.mappn.gfan";
                cls = "com.mappn.gfan.ui.SplashActivity";
                break;
            case R.id.bar_icon_settings: 
                pkg = "com.android.settings";
                cls = "com.android.settings.Settings";
                break;
        }

        if (pkg !="" && cls!="") {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySafely(intent);
            
        }
    }
}