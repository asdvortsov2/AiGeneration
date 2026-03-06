import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val sdf_fileSave = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
fun File.unzip(override:Boolean, targetFolder: File){
    ZipFile(path).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            zip.getInputStream(entry).use { input ->
                val targetFile = File(targetFolder.path+"/"+entry.name)
                targetFile.parentFile.mkdirs()
                if(targetFile.exists()){
                    if(override) targetFile.delete()
                    else return@use
                }
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
fun File.setPermissions(){
    setReadable(true, false)
    setWritable(true, false)
    setExecutable(true, false)
}
fun File.zip(targetFolder:File,limitFiles:Int?){
    val zipFileName =  "${nameWithoutExtension}_${sdf_fileSave.format(Date())}.zip"
    val zipFilePath = "${targetFolder}/$zipFileName"
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath))).use { out ->
        FileInputStream(this).use { fi ->
            BufferedInputStream(fi).use { origin ->
                val entry = ZipEntry(name)
                out.putNextEntry(entry)
                origin.copyTo(out, 1024)
            }
        }
    }
    File(zipFilePath).setPermissions()
    delete()
    limitFiles?.let{limit->
        val saveFiles = parentFile.listFiles()?.filter { it.extension == "zip" && it.nameWithoutExtension.substringBefore("_")  == nameWithoutExtension.substringBefore("_") }
        if((saveFiles?.size ?: 0) > limit){
            val toDel = saveFiles?.minByOrNull { it.name }
            toDel?.delete()
        }
    }

}
fun List<File>.zip(targetFolder:File,limitFiles:Int?, name:String){
    val zipFileName =  "${name}_${sdf_fileSave.format(Date())}.zip"
    val zipFilePath = "${targetFolder}/$zipFileName"
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFilePath))).use { out ->
        this.forEach { file ->
            FileInputStream(file).use { fi ->
                BufferedInputStream(fi).use { origin ->
                    val entry = ZipEntry(file.name)
                    out.putNextEntry(entry)
                    origin.copyTo(out, 1024)
                }
            }
        }

    }
    File(zipFilePath).setPermissions()

    limitFiles?.let{limit->
        val saveFiles = targetFolder.parentFile.listFiles()?.filter { it.extension == "zip" && it.nameWithoutExtension.substringBefore("_")  == name.substringBefore("_") }
        if((saveFiles?.size ?: 0) > limit){
            val toDel = saveFiles?.minByOrNull { it.name }
            toDel?.delete()
        }
    }

}
