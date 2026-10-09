package com.jsf.app.medication_solution.service

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

object VoiceAlarmManager {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var tempFile: File? = null

    fun startRecording(context: Context): File {
        val file = File(context.cacheDir, "family_alarm_temp.mp4")
        tempFile = file
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        recorder!!.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return file
    }

    fun stopRecording(): File? {
        runCatching { recorder?.stop(); recorder?.release() }
        recorder = null
        return tempFile
    }

    suspend fun uploadAlarmVoice(seniorId: String, file: File): Boolean {
        return runCatching {
            val ref = FirebaseStorage.getInstance().reference.child("alarms/$seniorId/family_alarm.mp4")
            ref.putFile(Uri.fromFile(file)).await()
            true
        }.getOrDefault(false)
    }

    suspend fun uploadFamilyNote(seniorId: String, file: File): Boolean {
        return runCatching {
            val ref = FirebaseStorage.getInstance().reference.child("voiceNotes/$seniorId/latest.mp4")
            ref.putFile(Uri.fromFile(file)).await()
            true
        }.getOrDefault(false)
    }

    suspend fun downloadAndPlay(context: Context, seniorId: String) {
        runCatching {
            val ref = FirebaseStorage.getInstance().reference.child("alarms/$seniorId/family_alarm.mp4")
            val localFile = File(context.cacheDir, "alarm_playback.mp4")
            ref.getFile(localFile).await()
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(localFile.absolutePath)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    suspend fun downloadAndPlayFamilyNote(context: Context, seniorId: String) {
        runCatching {
            val ref = FirebaseStorage.getInstance().reference.child("voiceNotes/$seniorId/latest.mp4")
            val localFile = File(context.cacheDir, "family_note.mp4")
            ref.getFile(localFile).await()
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(localFile.absolutePath)
                isLooping = false
                prepare()
                start()
            }
        }
    }

    fun stopPlayback() {
        runCatching { player?.stop(); player?.release() }
        player = null
    }

    fun isAlarmVoiceSaved(context: Context, seniorId: String): Boolean =
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .getBoolean("voice_saved_$seniorId", false)

    fun markAlarmVoiceSaved(context: Context, seniorId: String) {
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("voice_saved_$seniorId", true).apply()
    }

    fun isFamilyNoteSaved(context: Context, seniorId: String): Boolean =
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .getBoolean("note_saved_$seniorId", false)

    fun markFamilyNoteSaved(context: Context, seniorId: String) {
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("note_saved_$seniorId", true).apply()
    }

    // ── Per-medicine audio ────────────────────────────────────────────────────

    suspend fun uploadMedicineAudio(seniorId: String, medicationId: String, file: File): Boolean {
        return runCatching {
            val ref = FirebaseStorage.getInstance().reference
                .child("medicineAudio/$seniorId/$medicationId.mp4")
            ref.putFile(Uri.fromFile(file)).await()
            true
        }.getOrDefault(false)
    }

    suspend fun downloadAndPlayMedicineAudio(context: Context, seniorId: String, medicationId: String) {
        // Try medicine-specific audio first, fall back to global alarm
        val medRef = FirebaseStorage.getInstance().reference
            .child("medicineAudio/$seniorId/$medicationId.mp4")
        val globalRef = FirebaseStorage.getInstance().reference
            .child("alarms/$seniorId/family_alarm.mp4")
        runCatching {
            val localFile = File(context.cacheDir, "med_audio_$medicationId.mp4")
            val ref = runCatching { medRef.metadata.await(); medRef }.getOrDefault(globalRef)
            ref.getFile(localFile).await()
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(localFile.absolutePath)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    fun isMedicineAudioSaved(context: Context, seniorId: String, medicationId: String): Boolean =
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .getBoolean("med_audio_${seniorId}_$medicationId", false)

    fun markMedicineAudioSaved(context: Context, seniorId: String, medicationId: String) {
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("med_audio_${seniorId}_$medicationId", true).apply()
    }
}
