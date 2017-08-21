package de.robv.android.xposed.installer.installation;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import de.robv.android.xposed.installer.util.RootUtil;

import static de.robv.android.xposed.installer.util.InstallZipUtil.closeSilently;
import static de.robv.android.xposed.installer.util.InstallZipUtil.triggerError;
import static de.robv.android.xposed.installer.util.RootUtil.getShellPath;

public class FlashDirectly extends Flashable {
    private final boolean mSystemless;

    public FlashDirectly(File zipPath, FrameworkZips.Type type, String title, boolean systemless) {
        super(zipPath, type, title);
        mSystemless = systemless;
    }

    public void flash(Context context, FlashCallback callback) {
        // Open the ZIP file.
        ZipFile zip;
        try {
            zip = new ZipFile(mZipPath);
            Log.d("xposed-installer", "mZipPath => "+mZipPath);

        } catch (IOException e) {
            triggerError(callback, FlashCallback.ERROR_INVALID_ZIP, e.getLocalizedMessage());
            return;
        }

        // Do some checks.
        InstallZipUtil.ZipCheckResult zipCheck = InstallZipUtil.checkZip(zip);
        if (!zipCheck.isValidZip()) {
            triggerError(callback, FlashCallback.ERROR_INVALID_ZIP);
            closeSilently(zip);
            return;
        } else if (!zipCheck.isFlashableInApp()) {
            triggerError(callback, FlashCallback.ERROR_NOT_FLASHABLE_IN_APP);
            closeSilently(zip);
            return;
        }

        // Extract update-binary.
        ZipEntry entry = zip.getEntry("META-INF/com/google/android/update-binary");
        File updateBinaryFile = new File(XposedApp.getInstance().getCacheDir(), "update-binary");
        Log.d("xposed-installer", "updateBinaryFile => "+XposedApp.getInstance().getCacheDir()+"update-binary");
        try {
            AssetUtil.writeStreamToFile(zip.getInputStream(entry), updateBinaryFile, 0700);
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Could not extract update-binary", e);
            triggerError(callback, FlashCallback.ERROR_INVALID_ZIP);
            return;
        } finally {
            closeSilently(zip);
        }

        // Execute the flash commands.
        RootUtil rootUtil = new RootUtil();
        if (!rootUtil.startShell(callback)) {
            return;
        }

        callback.onStarted();

        rootUtil.execute("export NO_UIPRINT=1", callback);
        Log.d("xposed-installer", "execute => export NO_UIPRINT=1");
        if (mSystemless) {
            rootUtil.execute("export SYSTEMLESS=1", callback);
            Log.d("xposed-installer", "execute => export SYSTEMLESS=1");
        }

        int result = rootUtil.execute(getShellPath(updateBinaryFile) + " 2 1 " + getShellPath(mZipPath), callback);
        Log.d("xposed-installer", "execute => "+getShellPath(updateBinaryFile) + " 2 1 " + getShellPath(mZipPath));
        if (result != FlashCallback.OK) {
            triggerError(callback, result);
            return;
        }

        callback.onDone();
    }

    public static final Parcelable.Creator<FlashDirectly> CREATOR
            = new Parcelable.Creator<FlashDirectly>() {
        @Override
        public FlashDirectly createFromParcel(Parcel in) {
            return new FlashDirectly(in);
        }

        @Override
        public FlashDirectly[] newArray(int size) {
            return new FlashDirectly[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mSystemless ? 1 : 0);
    }

    protected FlashDirectly(Parcel in) {
        super(in);
        mSystemless = in.readInt() == 1;
    }
}
