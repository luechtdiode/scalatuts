package ch.seidel.sudoku

/**
 * After Martin Odersky's introduction into Scala FP at Coursera, this is a
 * decent repetition of all that immutable, streamy and functional stuff.
 * I tried to solve Sudoku puzzles in different sizes with one generic
 * datastructure.
 * It's implemented as a breath-first algo and not with the "Dancing Links" (DLX) 
 * -algorhytm (like that sample from google).
 * The DLX is much faster but (what I'v seen) not really FP-style 
 * (mutable vars/arrays/linked-lists). 
 * 
 * Perhaps this one can be tuned up to be as fast as the DLX-implementation.
 * 
 * @author Roland Seidel, May 2013
 */
object Sudoku {
  
  // typesystem
  
  type Cell = Char
  type Row = Int => Cell
  type Col = Int => Cell
  type Coord = (Int, Int)
  
  // helpers
  
  /**
   * Holder for Cell and Cell-Metadata
   * @param value the cell's value
   * @param pos the coordinate of the cell in the Grid
   * @param markupFun the function to retrieve all possibles values for that cell in the Grid
   */
  case class CellInfo(value: Cell, pos: Coord, markupFun: (Int,Int) => Set[Char]) {
    /**
     * @return true if cell's value is given and not a guess
     */
    lazy val isFix = value > 0
    
    /**
     * holds all possible values for that cell in current grid
     */ 
    lazy val markup: Set[Char] = if(!isFix) markupFun(pos._1, pos._2) else Set.empty
    
    /**
     * @return visualizes this instance by a stringrepresentation
     */
    override def toString = {
      "CI: fix="+isFix+", pos="+pos+", val="+value+", mark="+markup
    }
  }
  
  /**
   * Helper witch holds the indexes of a Cell (row or col) whereas: 
   * @param outer holds the index of the containing box (MiniGrid-Position)
   * @param inner holds the index of the Cell in the current box
   */
  private case class IndexSplit(outer: Int, inner: Int)
  
  /**
   * Helper function, that divide an absolute index of a Cell in the
   * BigGrid into an outer index witch represents the index of the containing box
   * and into an inner index witch represents the index of the cell in the box
   * @param index the absolute index of the cell in the BigGrid
   * @param clustersize amount of cells of a row/col per MiniGrid resp. 
   *                    the amount of MiniGrids in a row/col of an BigGrid
   * @return the Holder of the two indexes (inner,outer) as an IndexSplit Instance.
   */
  private def split(index: Int, clustersize: Int): IndexSplit = {
      // index 123 456 789
      // outer   0   1   2   
      // inner 123 123 123
    val outer = (index + clustersize) / clustersize -1
    val inner = index - outer * clustersize
    IndexSplit(outer, inner)
  }
  
  /**
   * Converts the numeric value to an displayable cell-symbol.
   * [0..9a..z@..]
   */
  def intToChar(n: Int): Char = 
    if(n < 10) n.toChar                       // digits 0..9 
    else if(n >= 36) (64 + n - 36).toChar     // special cases above the alphabet
    else (Char.char2int('a') + n - 10).toChar // chars a..z

  /**
   * Main-class Solver solves given List of Cell-List (char-matrix) 
   * @param given the unsolved Sudoku grid as a List of Rows where 
   *              each Row is a List of Cells
   */   
  case class Solver(given: List[List[Cell]]) {
    
      /**
     * calculated clustersize in the x-axis (how many boxes in a row)
     */
    val clusterXsize = math.sqrt(given.size.toDouble).toInt
    
    /**
     * calculated clustersize in the y-axis (how many boxes in a column)
     */
    val clusterYsize = math.sqrt(given(0).size.toDouble).toInt
    
    /**
     * shared instance of an empty Grid
     */
    lazy val emptyGrid = new MiniGrid(this, Map[Coord,Cell]())
    
    /**
     * Set of symbols, that are used in each row, col, box
     */
    lazy val symbols = (for(n <- (1 to clusterXsize * clusterYsize)) 
                        yield (intToChar(n))).toSet
    /**
     * Visualization of the given data-structure as an Grid 
     * (starting point for the Solver).                    
     */                    
    lazy val givenGrid = Grid(this, given)
    
    /**
     * Main-function witch solves the Sudoku
     * @return Stream of complete Solutions
     */
    lazy val solve: Stream[Grid] = _solve(givenGrid)
    
    /**
     * Inner recursive breath-first algo solving method
     */
    private def _solve(grid: BigGrid): Stream[BigGrid] = {
      def seek(candidates: Stream[BigGrid]): Stream[BigGrid] = {
        if(candidates.isEmpty) Stream.empty
        else {
          print(".")
          lazy val nextCandidates = for {
            cand <- candidates
            
            // sort and take 1 is performance-relevant!
            cellinfo <- cand.nextCandidates.sortBy(_.markup.size) take 1
            
            item <- cellinfo.markup.
              map(x => cand.updated(cellinfo.pos._1, cellinfo.pos._2, x))
          }
          yield item

          candidates #::: seek(nextCandidates)
        }
      }
      
      val ret = seek(Stream(grid)) filter(_.isComplete)
      println("*")
      ret
    }
  }
  
  /**
   * Base trait of a Grid
   */
  sealed trait Grid {
    
    /**
     * Reference to the Solver to access the symbol-table and the cluster-dimension
     */
    val solver: Solver
    
    /**
     * @return true if there is no empty cell in the Grid
     */
    lazy val isComplete: Boolean = !contains(0)
    
    // access to rows / cols / cells
    lazy val rows: Int = 0
    lazy val cols: Int = 0
    def row(index: Int): Row
    def col(index: Int): Col
    def cell(r: Int, c: Int): Cell = row(r)(c)
    
    lazy val cells: Stream[(Coord,Cell)] = 
      for {
        r <- (0 until rows).toStream
        c <- (0 until cols).toStream
      } 
      yield ((r, c), row(r)(c))
      
    def rowCells(index: Int): Stream[Cell] =
      (for(c <- 0 until cols) yield (row(index)(c))).toStream
//    lazy val rowCells: Int => Stream[Cell] = (index: Int) =>
//      (for(c <- 0 until cols) yield (row(index)(c))).toStream
    
    def colCells(index: Int): Stream[Cell] = 
      (for(r <- 0 until rows) yield (col(index)(r))).toStream
      
    def rowContains(r: Int, n: Int) = rowCells(r).contains(n)
    def colContains(c: Int, n: Int) = colCells(c).contains(n)
    def contains(n: Int) = ((0 until rows) flatMap(rowCells(_))).contains(n)
    
    /**
     * all symbols of the symbol-range except the fixed symbols already set in the grid / MiniGrid
     */
    lazy val reducedSymbols = for(n <- solver.symbols if(!contains(n))) yield n
    
    /**
     * Modify to a new copy by changing a Cell's Value at once ...
     * @param r Cell's Row-index
     * @param c Cell's Column-index
     * @param value Cell's Value
     * @return new Grid with the updated Value at given coordinate
     */
    def updated(r: Int, c: Int, value: Cell): Grid
    
    /**
     * Modify to a new copy by changing a MiniGrid at once ...
     * @param r MiniGrid's Row-index
     * @param c MiniGrid's Column-index
     * @param value Grid to replace at given coordinate
     * @return new BigGrid with the updated MiniGrid at given coordinate
     */
    def updated(r: Int, c: Int, value: Grid): Grid
    
    /**
     * Renders a row of a Grid
     * @param index Row-Index
     * @return row as String
     */
    def printRow(index: Int):String = {
      val s = rowCells(index).map(x => if(x == 0) " " else if(x < 10) f"$x%01d" else f"$x%c")
      s.mkString("|","|","|")
    }
    
    /**
     * Renders the Grid in block-format
     * @return Grid as String (can be reparsed by the Grid.apply() fn)
     */
    override def toString = {
      val s = for(r <- 0 until rows) yield(printRow(r))
      val h = (for(r <- 0 until rows) yield("-")).mkString("+", "+", "+")
      s.mkString(h + "\n", "\n", "\n" + h)
    }
    
    /**
     * Convenience-Bridge to the Constructor-Args of the GWT DLX Solver 
     */
    def toArray: Array[Array[Int]] = 
      (0 until rows).map(r => rowCells(r).map(x => x.toInt).toArray).toArray
  }
  
  /**
   * Holds all Cells of a Box in the Grid (Box eg SubGrid eg MiniGrid)
   * @param solver Reference to the Solver
   * @param cellMap raw-datastructure of the MiniGrid
   */
  case class MiniGrid(override val solver: Solver, cellMap: Map[Coord,Cell]) extends Grid {
    override lazy val rows = solver.clusterXsize
    override lazy val cols = solver.clusterYsize
    override def row(index: Int): Row = cellindex => cellMap.getOrElse((index,cellindex), 0)
    override def col(index: Int): Col = cellindex => cellMap.getOrElse((cellindex, index), 0)
    override def updated(r: Int, c: Int, value: Cell): Grid = MiniGrid(solver, cellMap.updated((r, c), value))
    override def updated(r: Int, c: Int, value: Grid): Grid = value
  }
  
  /**
   * Holds the entire Grid with its SubGrids/MiniGrids
   * @param solver Reference to the Solver
   * @param minigrids raw-datastructure of the BigGrid
   */
  case class BigGrid(override val solver: Solver, minigrids: Map[Coord,Grid]) extends Grid {
    override lazy val rows = getSubGrid(0,0).rows * solver.clusterXsize
    override lazy val cols = getSubGrid(0,0).cols * solver.clusterYsize
    override def row(index: Int): Row = cellindex => {
      val splitx = divideRow(index)
      val splity = divideCol(cellindex)
      cell(splitx, splity)
    }
    override def col(index: Int): Col = cellindex => {
      val splitx = divideRow(cellindex)
      val splity = divideCol(index)
      cell(splitx, splity)
    }
    override def updated(r: Int, c: Int, value: Cell): BigGrid = {
      val splitx = divideRow(r)
      val splity = divideCol(c)
      val toUpdate = getSubGrid(splitx, splity).updated(splitx.inner, splity.inner, value)
      BigGrid(solver, minigrids.updated((splitx.outer, splity.outer), toUpdate))
    }
    override def updated(r: Int, c: Int, value: Grid): BigGrid = {
      val splitx = divideRow(r)
      val splity = divideCol(c)
      val toUpdate = getSubGrid(splitx, splity).updated(splitx.inner, splity.inner, value)
      BigGrid(solver, minigrids.updated((splitx.outer, splity.outer), toUpdate))
    }

    private def divideRow(index: Int):IndexSplit = {
     split(index, solver.clusterXsize)
    }
  
    private def divideCol(index: Int):IndexSplit = {
     split(index, solver.clusterYsize)
    }
    private def cell(splitx: IndexSplit, splity:IndexSplit): Cell = {
      val minigrid = getSubGrid(splitx, splity)
      minigrid.row(splitx.inner)(splity.inner)
    }
    private def getSubGrid(x: IndexSplit, y: IndexSplit): Grid = {
      getSubGrid(x.outer, y.outer)
    }
    private def getSubGrid(x: Int, y: Int): Grid = {
      minigrids.getOrElse((x, y), solver.emptyGrid)
    }
    
    /**
     * Calculates all possible symbols for the given Cell-Coordinates
     * @param r Row-index
     * @param c Column-index
     * @return Set of possible symbols for the given Cell-Coordinates (cell-markup)
     */
    private def cellPossibilities(r: Int, c: Int) = {
      val n = row(r)(c)
      if(n > 0) Set[Cell]()
      else {
        val splitx = divideRow(r)
        val splity = divideCol(c)
        getSubGrid(splitx, splity).
          reducedSymbols.filter(x => !rowContains(r, x) && !colContains(c, x))
      }
    }
    
    /**
     * @return List of CellInfo's witch are candidates to do the next guess
     */
    lazy val nextCandidates = 
      cells.map(x => CellInfo(x._2, x._1, cellPossibilities)).
        filter(x => !x.isFix && x.markup.size > 0).
          toList
  }

  /**
   * Convenience Grid-Constructors
   */
  object Grid {
    def apply(solver: Solver, map: String): Grid = {
      val cleanrows = map.replace("-", "").replace("+", "").replace(" ", "").split("\n")
      val matrix = cleanrows.map(x => (x.split("\\|").map(n => n.charAt(0))).toList).toList
      if(matrix.isEmpty) solver.emptyGrid
      else if(matrix.size == solver.clusterXsize && matrix(0).size == solver.clusterYsize) {
        // MiniGrid
        MiniGrid(
            solver, (
            for{
              r <- 0 until matrix.size
              c <- 0 until matrix(r).size
            }
            yield {
              ((r,c),matrix(r)(c))
            }
            ).toMap[Coord,Cell])
      }
      else {
        // BigGrid
        apply(solver, matrix)
      }
    }

    def apply(solver: Solver, gridlist: List[List[Cell]]): BigGrid = {
      val m = (for {
        blockx <- 0 until solver.clusterXsize
        blocky <- 0 until solver.clusterYsize
        map = (for{
          row <- blockx * solver.clusterXsize until blockx * solver.clusterXsize + solver.clusterXsize
          col <- blocky * solver.clusterYsize until blocky * solver.clusterYsize + solver.clusterYsize
        }
        yield {
          ((row - blockx * solver.clusterXsize, col - blocky * solver.clusterYsize), gridlist(row)(col))
        }).toMap[Coord,Cell]
      }
      yield {
        ((blockx, blocky), MiniGrid(solver, map))
      })
      BigGrid(solver, m.toMap[Coord,Grid])
    }
  }
  
  def main(args: Array[String]) {
  }
}