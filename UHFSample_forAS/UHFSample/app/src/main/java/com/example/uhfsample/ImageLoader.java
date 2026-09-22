package com.example.uhfsample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight, zero-dependency Asynchronous Image Loader with LruCache memory caching.
 * Resolves full HTTP/HTTPS URLs and relative backend asset paths (/products/...).
 */
public class ImageLoader {
    private static final String TAG = "ImageLoader";
    private static ImageLoader sInstance;

    private final LruCache<String, Bitmap> mMemoryCache;
    private final ExecutorService mExecutor;
    private final Handler mMainHandler;

    public interface ImageCallback {
        void onImageLoaded(Bitmap bitmap, String url);
        void onError(Exception e);
    }

    private ImageLoader() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8; // Use 1/8th of the available memory
        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        mExecutor = Executors.newFixedThreadPool(4);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ImageLoader getInstance() {
        if (sInstance == null) {
            sInstance = new ImageLoader();
        }
        return sInstance;
    }

    /**
     * Resolves a potentially relative image URL using the backend base URL.
     */
    public static String resolveUrl(String rawUrl, String baseUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return "";
        }
        String u = rawUrl.trim();
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return u;
        }
        String b = baseUrl.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        return b + u;
    }

    /**
     * Loads an image into an ImageView with in-memory caching and URL tag tracking.
     */
    public void loadImage(final ImageView imageView, final String rawUrl, final String baseUrl, final int placeholderRes, final ImageCallback callback) {
        if (imageView == null) return;

        final String fullUrl = resolveUrl(rawUrl, baseUrl);
        if (fullUrl.isEmpty()) {
            imageView.setTag(null);
            if (placeholderRes != 0) {
                imageView.setImageResource(placeholderRes);
            }
            return;
        }

        // Check memory cache
        Bitmap cached = mMemoryCache.get(fullUrl);
        if (cached != null) {
            imageView.setTag(fullUrl);
            imageView.setImageBitmap(cached);
            if (callback != null) {
                callback.onImageLoaded(cached, fullUrl);
            }
            return;
        }

        // Set placeholder and tag
        imageView.setTag(fullUrl);
        if (placeholderRes != 0) {
            imageView.setImageResource(placeholderRes);
        }

        // Fetch asynchronously in background
        mExecutor.execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            try {
                URL url = new URL(fullUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);
                conn.setDoInput(true);
                conn.connect();

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    in = conn.getInputStream();
                    final Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        mMemoryCache.put(fullUrl, bitmap);
                        mMainHandler.post(() -> {
                            Object tag = imageView.getTag();
                            if (tag != null && fullUrl.equals(tag.toString())) {
                                imageView.setImageBitmap(bitmap);
                            }
                            if (callback != null) {
                                callback.onImageLoaded(bitmap, fullUrl);
                            }
                        });
                        return;
                    }
                }
                throw new Exception("HTTP response " + code);
            } catch (final Exception e) {
                Log.w(TAG, "Failed loading image: " + fullUrl + " - " + e.getMessage());
                mMainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
            } finally {
                if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        });
    }

    public void clearCache() {
        mMemoryCache.evictAll();
    }
}

