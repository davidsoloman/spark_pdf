package us.marek.pdf.inputformat

import org.apache.hadoop.io.{ LongWritable, Text }
import org.apache.hadoop.mapreduce.{ InputSplit, RecordReader, TaskAttemptContext }
import org.apache.hadoop.mapreduce.lib.input.FileSplit
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.pdf.PDFParser
import org.apache.tika.sax.BodyContentHandler

/* Unfortunately this is ugly (imperative), but Spark allows reading in binary formats
   via the Hadoop InputFormat API, which depends on a Hadoop RecordReader.
   Since Hadoop is written in Java, extending the RecordReader abstract class
   requires honoring its imperative contract.
   Also, Tika is Java-based and has an imperative API.
 */

/**
 * Hadoop RecordReader for PDFs.
 *
 * @author Marek Kolodziej
 * @since Dec. 11, 2014
 */
class PdfRecordReader extends RecordReader[LongWritable, TikaParsedPdfWritable] {

  private[this] var done = false
  private[this] val key: LongWritable = new LongWritable(1L)
  private[this] val value: TikaParsedPdfWritable = new TikaParsedPdfWritable()

  override def initialize(inputSplit: InputSplit, context: TaskAttemptContext): Unit =

    inputSplit match {

      case split: FileSplit => {

        val job = context.getConfiguration
        val file = split.getPath
        val fs = file.getFileSystem(job)
        val fileIS = fs.open(split.getPath)

        val metadata = new Metadata()
        val contentHandler = new BodyContentHandler(-1) // no size limit, default is 100000 characters
        val pdfParser = new PDFParser()
        pdfParser.parse(fileIS, contentHandler, metadata, new ParseContext())

        value.set(TikaParsedPdf(contentHandler, metadata))
      }

      case other => throw new IllegalArgumentException(
        s"inputSplit was of type ${other.getClass.getName} but FileSplit was expected"
      )
    }

  override def nextKeyValue: Boolean = {
    if (!done) {
      done = true
      true
    } else false
  }

  override def getCurrentKey: LongWritable = key

  override def getCurrentValue: TikaParsedPdfWritable = value

  override def getProgress: Float = 0.0F

  override def close = {}

}

