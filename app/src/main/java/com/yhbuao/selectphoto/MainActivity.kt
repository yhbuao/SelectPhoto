package com.yhbuao.selectphoto

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.design.widget.BottomSheetDialog
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var bottomSheetDialog: BottomSheetDialog? = null
    //三个常量全局标识
    //图库
    private val PHOTO_PHOTOALBUM = 0
    //拍照
    private val PHOTO_TAKEPHOTO = 1
    //裁剪
    private val PHOTO_PHOTOCLIP = 2

    // 图片拍照的标识,1拍照0相册
    private var TAKEPAHTO = 1
    /**
     * 裁剪图片的的地址，最终加载它
     * 用于拍照完成或者选择本地图片之后
     */
    private var uriClipUri: Uri? = null
    /**
     * 7.0获取的图片地址，与7.0之前方式不一样
     */
    private var takePhotoSaveAdr: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        iv_userPhoto.setOnClickListener { showPop() }
    }

    private fun showPop() {
        bottomSheetDialog = BottomSheetDialog(this)
        val selectView = LayoutInflater.from(this).inflate(R.layout.pop_layout, null, false)
        bottomSheetDialog?.setContentView(selectView)
        selectView.findViewById<TextView>(R.id.tv_takePhoto).setOnClickListener { getRootPermissions(PHOTO_TAKEPHOTO) }
        selectView.findViewById<TextView>(R.id.tv_selectPhoto)
            .setOnClickListener { getRootPermissions(PHOTO_PHOTOALBUM) }
        selectView.findViewById<TextView>(R.id.tv_cancel).setOnClickListener { bottomSheetDialog?.dismiss() }
        bottomSheetDialog?.show()
    }

    /**
     *相机拍照
     */
    private fun taskPhoto() {
        TAKEPAHTO = 1
        // 启动系统相机
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val mImageCaptureUri: Uri
        // 判断7.0android系统
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //临时添加一个拍照权限
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            //通过FileProvider获取uri
            takePhotoSaveAdr = FileProvider.getUriForFile(
                this@MainActivity,
                packageName, File(Environment.getExternalStorageDirectory(), "savephoto.jpg")
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoSaveAdr)
        } else {
            mImageCaptureUri = Uri.fromFile(File(Environment.getExternalStorageDirectory(), "savephoto.jpg"))
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri)
        }
        startActivityForResult(intent, PHOTO_TAKEPHOTO)
        bottomSheetDialog?.dismiss()
    }

    private fun selecetPhoto() {
        TAKEPAHTO = 0
        //调用系统图库，选择图片
        //Intent.ACTION_PICK 意思是选择数据，其具体表达有：
        // Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        //intent.setType("image/*"); 获取本地图片
        // intent.setType("video/*");  获取本地视频
        //intent.setType("audio/*")  获取本地音乐

        // Intent intent = new Intent(Intent.ACTION_PICK);
        //  intent.setType(ContactsContract.Contacts.CONTENT_TYPE); //获取联系人
        // startActivityForResult(intent, PICK_CONTACT);

        //也可以这样写
        val intentAlbum = Intent(Intent.ACTION_PICK, null)
        //其中External为sdcard下的多媒体文件,Internal为system下的多媒体文件。
        //使用INTERNAL_CONTENT_URI只能显示存储在内部的照片
        intentAlbum.setDataAndType(
            MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*"
        )
        //返回结果和标识
        startActivityForResult(intentAlbum, PHOTO_PHOTOALBUM)
        bottomSheetDialog?.dismiss()
    }

    /**
     * 获取6.0读取文件的权限
     */
    private fun getRootPermissions(typeCode: Int) {
        //2.0版本去掉Manifest.permission.ACCESS_COARSE_LOCATION,
        val rxPermissions = RxPermissions(this) // where this is an Activity instance
        rxPermissions.request(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
        )
            .subscribe(Consumer<Boolean> { granted ->
                if (granted) { // 在android 6.0之前会默认返回true
                    // 已经获取权限
                    Log.e(TAG, "已经获取权限")
                    when (typeCode) {
                        PHOTO_TAKEPHOTO -> taskPhoto()
                        PHOTO_PHOTOALBUM -> selecetPhoto()
                    }
                } else {
                    // 未获取权限
                    Log.e(TAG, "您没有授权该权限，请在设置中打开授权")
                    Toast.makeText(this@MainActivity, "您没有授权该权限，请在设置中打开授权", Toast.LENGTH_LONG).show()
                }
            }, Consumer<Throwable> { }, Action { Log.e(TAG, "{run}") })
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {//避免选图时取消操作
            when (requestCode) {
                PHOTO_TAKEPHOTO -> {
                    var clipUri: Uri? = null
                    //判断如果是7.0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (takePhotoSaveAdr != null) {
                            clipUri = takePhotoSaveAdr
                        }
                    } else {
                        clipUri =
                                Uri.fromFile(File(Environment.getExternalStorageDirectory().toString() + "/savephoto.jpg"))
                    }
                    //获取拍照结果，执行裁剪
                    startPhotoZoom(clipUri)
                }
                PHOTO_PHOTOALBUM ->
                    //获取图库结果，执行裁剪
                    startPhotoZoom(data!!.data)
                PHOTO_PHOTOCLIP -> {
                    //注：glide版本 >=4.4 加载同一个图片，地址不变，会有缓存，加载的还是之前的一张
                    //裁剪完成后的操作，上传至服务器或者本地设置
                    val optionsa = RequestOptions()
                    optionsa.placeholder(R.mipmap.ic_launcher)
                    optionsa.error(R.mipmap.ic_launcher_round)    //异常显示图
                    optionsa.diskCacheStrategy(DiskCacheStrategy.NONE)//禁用掉Glide的缓存功能
                    optionsa.skipMemoryCache(true)//禁用掉Glide的内存缓存
                    Glide.with(this).load(uriClipUri).apply(optionsa).into(iv_userPhoto)
                }
            }
        }
    }

    /**
     * 图片裁剪的方法
     * @param uri
     */
    private fun startPhotoZoom(uri: Uri?) {
        Log.e("uri=====", "" + uri)
        //com.android.camera.action.CROP，这个action是调用系统自带的图片裁切功能
        val intent = Intent("com.android.camera.action.CROP")
        intent.setDataAndType(uri, "image/*")//裁剪的图片uri和图片类型
        intent.putExtra("crop", "true")//设置允许裁剪，如果不设置，就会跳过裁剪的过程，还可以设置putExtra("crop", "circle")
        intent.putExtra("aspectX", 1)//裁剪框的 X 方向的比例,需要为整数
        intent.putExtra("aspectY", 1)//裁剪框的 Y 方向的比例,需要为整数
        intent.putExtra("outputX", 60)//返回数据的时候的X像素大小。
        intent.putExtra("outputY", 60)//返回数据的时候的Y像素大小。
        //uriClipUri为Uri类变量，实例化uriClipUri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (TAKEPAHTO == 1) {//如果是7.0的拍照
                //开启临时访问的读和写权限
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                //针对7.0以上的操作
                intent.clipData = ClipData.newRawUri(MediaStore.EXTRA_OUTPUT, uri)
                uriClipUri = uri
            } else {//如果是7.0的相册
                //设置裁剪的图片地址Uri
                uriClipUri =
                        Uri.parse("file://" + "/" + Environment.getExternalStorageDirectory().path + "/" + "clip.jpg")
            }

        } else {
            uriClipUri = Uri.parse("file://" + "/" + Environment.getExternalStorageDirectory().path + "/" + "clip.jpg")
        }
        Log.e("uriClipUri=====", "" + uriClipUri)
        //Android 对Intent中所包含数据的大小是有限制的，一般不能超过 1M，否则会使用缩略图 ,所以我们要指定输出裁剪的图片路径
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uriClipUri)
        intent.putExtra("return-data", false)//是否将数据保留在Bitmap中返回
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())//输出格式，一般设为Bitmap格式及图片类型
        intent.putExtra("noFaceDetection", true)//人脸识别功能
        startActivityForResult(intent, PHOTO_PHOTOCLIP)//裁剪完成的标识
    }

    private var photoBitmap: Bitmap? = null
    private var file: File? = null
    /**
     * 上传图片
     */
    private fun upDateFile() {
        try {
            //裁剪后的图像转成BitMap
            photoBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uriClipUri))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        //创建路径
        val path = Environment.getExternalStorageDirectory()
            .path + "/Pic"
        //获取外部储存目录
        file = File(path)
        //创建新目录, 创建此抽象路径名指定的目录，包括创建必需但不存在的父目录。
        file?.mkdirs()
        //以当前时间重新命名文件
        val i = System.currentTimeMillis()
        //生成新的文件
        file = File(file.toString() + "/" + i + ".png")
        Log.e("fileNew", file?.path)
        //创建输出流
        var out: OutputStream? = null
        try {
            out = FileOutputStream(file?.path)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        //压缩文件，返回结果
        val bCompress = photoBitmap?.compress(Bitmap.CompressFormat.JPEG, 100, out)
    }

    /**
     * 另外一种图片压缩的方法
     */
    private fun compressPhto(mFile: File) {
        //BitmapFactory这个类就提供了多个解析方法（decodeResource、decodeStream、decodeFile等）用于创建Bitmap。
        //比如如果图片来源于网络，就可以使用decodeStream方法；
        //如果是sd卡里面的图片，就可以选择decodeFile方法；
        //如果是资源文件里面的图片，就可以使用decodeResource方法等
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true // 获取当前图片的边界大小
        val outHeight = options.outHeight
        val outWidth = options.outWidth
        val outMimeType = options.outMimeType
        options.inJustDecodeBounds = false
        //inSampleSize的作用就是可以把图片的长短缩小inSampleSize倍，所占内存缩小inSampleSize的平方
        options.inSampleSize = calculateSampleSize(options, 10, 10)
        val bitmap = BitmapFactory.decodeFile(mFile.path, options)
        iv_userPhoto.setImageBitmap(bitmap)
    }

    /**
     * 计算出所需要压缩的大小
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        val picWidth = options.outWidth
        val picHeight = options.outHeight
        if (picWidth > reqWidth || picHeight > reqHeight) {
            val halfPicWidth = picWidth / 2
            val halfPicHeight = picHeight / 2
            while (halfPicWidth / sampleSize > reqWidth || halfPicHeight / sampleSize > reqHeight) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}
