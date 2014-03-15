package ch.seidel.pdm.system

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import ch.seidel.pdm.PDMPattern._
import java.io.File
import java.util._
import org.apache.log4j.Logger

class PersistenceTempFileImpl extends Persistence[Abonnement, Work] {
  lazy val tempFolder = File.createTempFile("pdm", "abos").getParent()
  val logger = Logger.getLogger(this.getClass())
  
  override def saveAbos(abos: Map[String, Abonnement]) = {
    val toSave = new HashMap[String, Abonnement]
    toSave.putAll(abos)
    val fos = new FileOutputStream(aboFile)
    try {
      val os = new ObjectOutputStream(fos)
      os.writeObject(toSave)
      os.flush();
      logger.info("Abonnements saved to " + aboFile)
    }
    finally {
      fos.close();
    }
  }
  
  override def loadAbos(): Map[String, Abonnement] = {
    if(aboFile.exists()) {
      val fis = new FileInputStream(aboFile)
      val is = new ObjectInputStream(fis)
      try {
        val abos = is.readObject().asInstanceOf[Map[String, Abonnement]]
        logger.info("Abonnements loaded from " + aboFile)
        abos
      }
      finally {
        fis.close();
      }
    }
    else {
      new HashMap[String, Abonnement]()
    }
  }
  
  override def saveQueue(aboId: String, queue: List[Work]) = {
    val toSave = new ArrayList[Work]
    toSave.addAll(queue)
    
    val file = queueFile(aboId)
    val fos = new FileOutputStream(file)
    try {
      val os = new ObjectOutputStream(fos)
      os.writeObject(toSave)
      os.flush();
      logger.info("queue saved to " + file)    
    }
    finally {
      fos.close();
    }
  }
  
  override def loadQueue(aboId: String): List[Work] = {
    val file = queueFile(aboId)
    if(file.exists()) {
      val fis = new FileInputStream(file)
      try {
        val is = new ObjectInputStream(fis)
        val queue = is.readObject().asInstanceOf[List[Work]]
        logger.info("queue loaded from " + file)
        queue
      }
      finally {
        fis.close();
      }
    }
    else {
      new ArrayList[Work]()
    }
  }
  
  private def queueFile(aboId: String) = {
    val queDir = new File(tempFolder + "/queues")
    if(!queDir.exists()) {
      queDir.mkdir()
    }
    new File(s"${queDir}/${aboId}.queue")
  }
  
  private lazy val aboFile = {
    val aboDir = new File(tempFolder + "/abos")
    if(!aboDir.exists()) {
      aboDir.mkdir()
    }
    new File(s"${aboDir}/abonnements.pub")
  }
  
}