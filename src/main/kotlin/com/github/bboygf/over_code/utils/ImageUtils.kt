package com.github.bboygf.over_code.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO


import com.intellij.util.ui.UIUtil

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
                val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image
                if (image != null) {
                    val bufferedImage = toBufferedImage(image)
                    return bufferedImage.toBase64()
                }
            }
            // (可选) 检查剪贴板是否有文件列表，且文件是图片
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
                val file = files?.firstOrNull() as? File
                if (file != null && isImageFile(file)) {
                    val fileImage = ImageIO.read(file)
                    return fileImage?.toBase64()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 将普通的 java.awt.Image 转换为 BufferedImage
     */
    private fun toBufferedImage(img: java.awt.Image): BufferedImage {
        if (img is BufferedImage) {
            return img
        }
        val width = img.getWidth(null).takeIf { it > 0 } ?: 1
        val height = img.getHeight(null).takeIf { it > 0 } ?: 1
        val bimage = UIUtil.createImage(null, width, height, BufferedImage.TYPE_INT_ARGB)
        val bGr = bimage.createGraphics()
        bGr.drawImage(img, 0, 0, null)
        bGr.dispose()
        return bimage
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