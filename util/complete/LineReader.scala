/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

	import jline.{Completor, ConsoleReader, History}
	import java.io.{File,PrintWriter}
	import complete.Parser
	
abstract class JLine extends LineReader
{
	protected[this] val handleCONT: Boolean
	protected[this] val reader: ConsoleReader
	protected[this] val historyPath: Option[File]

	def readLine(prompt: String, mask: Option[Char] = None) = JLine.withJLine { unsynchronizedReadLine(prompt, mask) }

	private[this] def unsynchronizedReadLine(prompt: String, mask: Option[Char]) =
		readLineWithHistory(prompt, mask) match
		{
			case null => None
			case x => Some(x.trim)
		}

	private[this] def readLineWithHistory(prompt: String, mask: Option[Char]): String =
		historyPath match
		{
			case None => readLineDirect(prompt, mask)
			case Some(file) =>
				val h = reader.getHistory
				JLine.loadHistory(h, file)
				try { readLineDirect(prompt, mask) }
				finally { JLine.saveHistory(h, file) }
		}

	private[this] def readLineDirect(prompt: String, mask: Option[Char]): String =
		if(handleCONT)
			Signals.withHandler(() => resume(), signal = Signals.CONT)( () => readLineDirectRaw(prompt, mask) )
		else
			readLineDirectRaw(prompt, mask)
	private[this] def readLineDirectRaw(prompt: String, mask: Option[Char]): String =
		mask match {
			case Some(m) => reader.readLine(prompt, m)
			case None => reader.readLine(prompt)
		}
	private[this] def resume()
	{
		jline.Terminal.resetTerminal
		JLine.terminal.disableEcho()
		reader.drawLine()
		reader.flushConsole()
	}
}
private object JLine
{
	// When calling this, ensure that enableEcho has been or will be called.
	//  getTerminal will initialize the terminal to disable echo.
	private def terminal = jline.Terminal.getTerminal
	private def withTerminal[T](f: jline.Terminal => T): T =
		synchronized
		{
			val t = terminal
			t.synchronized { f(t) }
		}
	def createReader() =
		withTerminal { t =>
			val cr = new ConsoleReader
			t.enableEcho()
			cr.setBellEnabled(false)
			cr
		}
	def withJLine[T](action: => T): T =
		withTerminal { t =>
			t.disableEcho()
			try { action }
			finally { t.enableEcho() }
		}
	private[sbt] def loadHistory(h: History, file: File)
	{
		h.setMaxSize(MaxHistorySize)
		if(file.isFile) IO.reader(file)( h.load )
	}
	private[sbt] def saveHistory(h: History, file: File): Unit =
		Using.fileWriter()(file) { writer =>
			val out = new PrintWriter(writer, false)
			h.setOutput(out)
			h.flushBuffer()
			out.close()
			h.setOutput(null)
		}

	def simple(historyPath: Option[File], handleCONT: Boolean = HandleCONT): SimpleReader = new SimpleReader(historyPath, handleCONT)
	val MaxHistorySize = 500
	val HandleCONT = !java.lang.Boolean.getBoolean("sbt.disable.cont") && Signals.supported(Signals.CONT)
}

trait LineReader
{
	def readLine(prompt: String, mask: Option[Char] = None): Option[String]
}
final class FullReader(val historyPath: Option[File], complete: Parser[_], val handleCONT: Boolean = JLine.HandleCONT) extends JLine
{
	protected[this] val reader =
	{
		val cr = new ConsoleReader
		cr.setBellEnabled(false)
		sbt.complete.JLineCompletion.installCustomCompletor(cr, complete)
		cr
	}
}

class SimpleReader private[sbt] (val historyPath: Option[File], val handleCONT: Boolean) extends JLine
{
	protected[this] val reader = JLine.createReader()
}
object SimpleReader extends SimpleReader(None, JLine.HandleCONT)

