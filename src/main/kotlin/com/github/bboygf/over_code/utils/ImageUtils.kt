package com.github.bboygf.over_code.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO


/**
 * 图片处理工具类
 */
object ImageUtils {

    /**
     * 尝试从剪贴板读取图片
     * @return 图片的 Base64 字符串 (不带前缀)，如果没有图片则返回 null
     */
    fun getClipboardImageBase64(): String? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        try {
            // 检查剪贴板是否有图片
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage
                return image?.toBase64()
            }
            // (可选) 检查剪贴板是否有文件列表，且文件是图片
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
                val file = files?.firstOrNull() as? File
                if (file != null && isImageFile(file)) {
                    return ImageIO.read(file).toBase64()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 将 BufferedImage 转换为 Base64 字符串
     */
    private fun BufferedImage.toBase64(format: String = "png"): String {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(this, format, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * 简单的文件扩展名检查
     */
    private fun isImageFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".png") || name.endsWith(".jpg") ||
                name.endsWith(".jpeg") || name.endsWith(".bmp")
    }
}