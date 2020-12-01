package com.example.ocrtts

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.googlecode.tesseract.android.TessBaseAPI

class MyModel internal constructor() {
    //const
    val PICTURE_REQUEST_CODE = 100
    val CREATE_REQUEST_CODE = 101
    val EDIT_REQUEST_CODE = 102
    val FOLDER_REQUEST_CODE = 103
    val VIEW_RESULT_SET = 0
    val VIEW_READING_STATE = 1
    val VIEW_READ_HIGHLIGHT = 2
    val VIEW_RESET = 3
    val VIEW_BUTTON_IMG = 4
    val VIEW_PROGRESS_ING = 5
    val VIEW_TRANS_DONE = 6
    val TAG = "TextToSpeech"
    val MIME_TEXT = "text/plain"

    //OCR
    var sTess: TessBaseAPI? = null
    var dataPath = "" //can
    var lang = "kor"//can
    var ocrIndex = -1 //OCR 진행 중인 이미지 번호
    var threadIndex = 0 //thread 시행횟수

    //Text, TTS
    var ocrResult = " " //OCR 결과값 받음
    var bigText = BigText()//Sentence를 가지는 class
    var readIndex = 0 //읽고있는 문장 넘버
    var state = "stop"//playing, stop
    var charSum = 0 //읽고 있는 글자의 view에서의 위치
    var readSpeed = 3.0 //tts 기본속도 설정
    var readState = "현재 문장 " + bigText.size + " 중 " + readIndex + "번 문장"

    //Data
    var frw = SAFRW()
    var safUri: Uri? = null
//    var title = "no title"
    val folderMetaList = arrayListOf<FolderMeta>()
    val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

    //Service
    var mIsBound = false

    private fun allocClipData(requestCode: Int, data: Intent?, main: MainActivity) {
        val curs: Cursor?
        val folder = folderMetaList.first()

        when(requestCode) {
            PICTURE_REQUEST_CODE -> {
                if (data != null) {
                    if (data.data != null) {
                        // 이미지 한 장만 선택했을 때
                        folder.uriList.add(data.data!!)
                        Log.i("DB", "clipData : " + folder.uriList)
                    } else if (data.clipData != null)
                        for (i in 0 until data.clipData!!.itemCount)
                            folder.uriList.add(data.clipData!!.getItemAt(i).uri)
                }
            }
            FOLDER_REQUEST_CODE -> {
                Log.i("DB", "FOLDER_REQUEST_CODE")
            }
        }
        if (folder.uriList.isNotEmpty()) {
            curs = main.contentResolver.query(folder.uriList[0],
                    arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                    null, null, null)
            if (curs!!.moveToNext()) {
                Log.i("title: ", curs.getString(0))
                folder.title = curs.getString(0)
            }
            curs.close()
        }
    }

    fun sumTotalPage(): Int {
        var sum = 0

        for (e in folderMetaList) sum += e.folderTotalPages
        return sum
    }

    fun runOCR(requestCode: Int, data: Intent?, main: MainActivity) {
        // OCR translate
        val thread: OCR
        val folder = folderMetaList.first()

        // picked image list allocate
        allocClipData(requestCode, data, main)
        // image meta data parsing
        // TODO 폴더 단위 변환이면 OCR에서 매 폴더마다 체크해주자.
        folder.page = main.myDBOpenHelper!!.getContinuePage(folder.title)
        Log.i("runOCR", "선택한 폴더(책 제목) : " + folder.title)
        folder.pickedNumber = folder.uriList.size
        if (main.myDBOpenHelper!!.isNewTitle(folder.title)) {
            folder.isPageUpdated = false
            Toast.makeText(main, "변환을 시작합니다.", Toast.LENGTH_LONG).show()
        }
        else if (folder.page < folder.pickedNumber) {
            folder.isPageUpdated = false
            Toast.makeText(main, "이전 변환에 이어서 변환합니다.", Toast.LENGTH_LONG).show()
        }
        else Toast.makeText(main, "완료한 변환입니다.\n다시 변환을 원할 시 변환 기록을 지워주세요", Toast.LENGTH_LONG).show()
        if (folder.pickedNumber > 0) {
            threadIndex++ //생성한 스레드 수
            folder.folderTotalPages = folder.pickedNumber - folder.page
            for (e in folderMetaList) Log.i("OCR", "uriList size : " + e.uriList.size)
            thread = OCR(main) // OCR 진행할 스레드
            thread.isDaemon = true
            thread.start()
        } else Log.i("DB", "pickedNumber가 0임")
    }
}