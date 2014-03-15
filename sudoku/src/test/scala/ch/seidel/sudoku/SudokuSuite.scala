package ch.seidel.sudoku

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

import ch.seidel.sudoku.Sudoku._

@RunWith(classOf[JUnitRunner])
class SudokuSuite extends FunSuite {
  test("simple 4x4") {
    // start small and easy ... 
    val solver = Solver(
        List(
            List(0,0,0,0),
            List(0,0,2,1),
            List(3,0,0,4),
            List(0,0,0,0)
            ))
    println("Given:\n" + solver.givenGrid + "\n")
    val solutions = solver.solve.toList      
    println(solutions.size + " Result(s):\n" + (solver.solve take 10).mkString("\n"))
    assert(solver.solve.toList(0).toString === Grid(solver, 
        """2|1|4|3
           4|3|2|1
           3|2|1|4
           1|4|3|2""").toString)           
  }
  
  test("empty 4x4") {
    // start small and easy ... 
    val solver = Solver(
        List(
            List(0,0,0,0),
            List(0,0,0,0),
            List(0,0,0,0),
            List(0,0,0,0)
            ))
    println("Given:\n" + solver.givenGrid + "\n")        
    val solutions = solver.solve.toList      
    println(solutions.size + " Result(s):\n" + (solver.solve take 10).mkString("\n"))
    assert(solver.solve.toList.size === 288)           
  }
  
  test("hard 9x9 one solution") {
    // some more effort - convenient Sudoku from magazin (titled as hard, but one solution)
    val solver = Solver(
        List(
            List(0,0,0,8,0,0,6,7,0),
            List(0,0,0,0,0,6,0,0,3),
            List(0,0,0,4,0,9,0,0,5),
            List(5,0,1,0,0,7,4,0,0),
            List(0,0,0,0,4,0,0,0,0),
            List(4,7,6,1,0,5,8,0,2),
            List(1,0,0,9,0,2,0,0,0),
            List(7,0,0,0,0,4,0,0,0),
            List(0,9,8,0,0,3,0,0,0)
            ))
    println("Given:\n" + solver.givenGrid + "\n")        
    println(solver.solve.toSet.size + " Result(s):\n" + (solver.solve take 10).toList.mkString("\n"))
    assert(solver.solve.toList.size === 1)
        assert(solver.solve.toList(0).toString === Grid(solver, 
         """3|2|4|8|5|1|6|7|9
            8|5|9|2|7|6|1|4|3
            6|1|7|4|3|9|2|8|5
            5|8|1|3|2|7|4|9|6
            9|3|2|6|4|8|7|5|1
            4|7|6|1|9|5|8|3|2
            1|4|5|9|8|2|3|6|7
            7|6|3|5|1|4|9|2|8
            2|9|8|7|6|3|5|1|4""").toString)           
      //    // benchmark the same Grid with the GWT DLX-impl.:
      //    val mss = System.currentTimeMillis()
      //    val solved = new Solver(new Sudoku(Grid(solver33, solver33.given).toArray).solve.map(x => x.toList.map(x => x.toChar)).toList).givenGrid
      //    println("Result found by GWT-Solver (DLX) in " + (System.currentTimeMillis() - mss) + "ms:\n" + solved)
  }
  
  test("hard 9x9 multiple solutions") {
    // now pretty hard example with more than 400 possible solutions
    val solver = Solver(
        List(
            List(0,5,0,0,0,0,0,4,0),
            List(1,0,0,4,0,9,0,0,5),
            List(0,0,8,0,6,0,9,0,0),
            List(0,3,0,0,0,0,0,5,0),
            List(0,0,7,0,9,0,4,0,0),
            List(0,9,0,0,0,0,0,7,0),
            List(0,0,1,0,4,0,2,0,0),
            List(9,0,0,8,0,1,0,0,4),
            List(0,6,0,0,0,0,0,8,0)
            ))
    println("Given:\n" + solver.givenGrid + "\n")        
    println(solver.solve.toSet.size + " Result(s):\n" + (solver.solve take 10).toList.mkString("\n"))
    assert(solver.solve.toList.size === 418)
  }
  
  test("hard 16 x 16 with two solutions") {
    val solver = Solver(
        // see http://www.sudokugenerator.de
        List(
//            List(1,2,3,4,5,6,7,8,9,'a','b','c','d','e','f','g'),
            List(0,0,'c',0, 0,0,0,9,     7,0,0,2,       0,0,6,3),
            List(0,0,1,4,   2,'a',0,'c', 'f',0,'b','e', 'd','g',0,5),
            List(2,0,'e',8, 'b',6,'g','f', 9,0,5,'d',   0,0,4,7),
            List(0,'d',0,0, 'e',0,0,0,   0,0,0,'g',     9,0,1,2),
            
            List(0,0,0,6,   7,0,9,4,     1,0,'g','a', 0,8,'d','f'),
            List(4,'a',0,7, 8,'f',6,0,   2,0,9,0,     0,5,'c',0),
            List(8,0,0,'d', 0,1,'a','b', 4,0,0,0,     0,9,0,'e'),
            List('e',0,0,0, 0,2,0,0,     0,0,0,8,     0,0,0,4),
            
            List(0,8,4,0,     'c','b',5,7, 'a',0,0,0, 0,'d','f',0),
            List(0,0,6,0,     0,'g',8,2,   'e',0,1,4, 0,0,'b','c'),
            List(0,0,'b','f', 0,'e',1,0,   5,'c',0,0, 'g',0,9,0),
            List('d','c',0,5, 0,0,0,3,     8,0,0,6,   'e',0,7,0),
            
            List(9,1,'a',3, 'f','d',0,0, 6,8,0,5,     4,7,0,'b'),
            List(0,0,0,'b', 0,9,3,8,     0,2,0,0,     'c',1,5,'a'),
            List('g',4,0,2, 0,'c',0,'a', 0,0,7,0,     0,0,3,0),
            List(5,0,0,0,   4,7,0,0,     'd',0,'e',3, 0,0,0,0)
            ))
    println("Given:\n" + solver.givenGrid + "\n")  
    val ms2 = System.currentTimeMillis()
    val s2 = solver.solve.toList
    println(s2.size + " Result(s) found in " + (System.currentTimeMillis() - ms2) + "ms:\n" + s2.mkString("\n"))
    assert(s2.size === 2)
  }
  
  test("ultimate 32 x 32 with two solutions") {
        // and finally the ultimate 36 x 36 Matrix (zero'es are the empty cells)
    val sudokutxt = 
  """0|x|y|9|m|a|p|g|8|0|j|b|0|c|1|i|0|d|n|l|6|e|0|0|w|r|7|q|5|3|v|0|o|s|0|k
    |0|4|i|r|1|e|0|l|0|0|5|d|0|0|0|o|g|y|b|0|0|s|2|0|c|m|h|0|k|u|0|0|8|0|z|n
    |o|p|0|0|z|0|7|@|1|m|0|6|5|9|l|4|3|0|0|k|w|0|d|8|2|i|0|g|t|0|f|r|c|u|e|0
    |0|l|0|@|0|d|0|0|0|o|0|0|0|p|8|h|n|m|0|1|c|0|0|j|0|0|v|0|s|f|0|0|5|0|y|0
    |h|j|5|c|g|q|k|z|s|0|0|t|e|0|0|0|6|0|0|v|9|u|o|f|8|d|0|4|0|p|0|1|0|2|i|l
    |0|6|0|0|0|8|0|3|w|v|0|4|f|0|r|s|0|0|5|@|i|0|p|0|l|z|0|0|e|o|9|q|b|0|0|d
    |z|2|0|4|a|0|8|u|c|k|0|l|t|0|0|m|0|v|0|3|o|p|f|x|0|j|0|0|7|5|0|n|w|g|0|i
    |r|0|7|k|e|0|i|q|0|3|y|n|w|4|g|u|0|z|0|2|5|j|s|0|0|0|d|0|m|0|p|6|l|f|0|0
    |l|5|3|0|x|0|0|0|0|j|p|0|o|s|0|0|7|e|0|0|0|q|0|b|f|a|c|0|4|t|0|2|0|k|v|z
    |m|0|f|0|8|t|o|d|7|s|6|r|0|0|3|0|p|1|w|g|k|a|z|l|0|h|i|0|x|0|0|4|0|@|j|e
    |g|i|@|n|0|0|9|0|0|0|0|z|j|a|x|l|0|f|c|d|0|4|m|7|3|k|p|r|0|w|t|0|0|o|0|8
    |b|0|j|0|0|p|0|a|0|4|0|5|0|0|2|0|@|0|8|0|e|0|0|t|z|u|g|v|0|q|1|c|0|0|0|s
    |n|0|0|0|i|s|x|h|0|z|w|1|6|e|9|0|0|b|3|7|l|o|c|0|0|y|5|u|0|v|0|a|0|j|0|2
    |4|0|z|j|f|2|c|5|y|e|3|s|0|8|0|q|d|@|g|6|b|1|0|0|0|n|0|p|i|0|0|0|m|v|r|0
    |@|0|a|5|6|m|d|b|2|8|0|v|c|0|p|0|u|n|0|0|s|0|i|0|j|9|0|0|0|x|k|0|0|0|3|g
    |3|d|p|0|0|h|j|o|0|0|0|0|4|w|7|g|f|x|0|9|@|n|0|2|0|l|b|c|a|s|0|0|e|y|8|1
    |v|o|0|u|c|7|0|k|l|0|4|0|a|0|z|j|h|0|p|5|x|t|q|0|1|@|r|e|8|2|w|s|6|d|n|f
    |x|y|0|1|w|0|0|0|a|0|f|u|0|2|5|0|s|k|0|0|d|0|0|h|0|4|6|0|3|0|o|9|0|c|0|p
    |s|f|0|x|0|3|0|0|0|0|a|y|0|m|0|0|v|0|o|j|z|0|b|n|0|c|u|0|q|7|e|0|r|8|0|w
    |0|v|0|0|d|4|0|9|z|i|8|e|2|0|0|0|c|r|0|0|q|7|0|s|0|o|0|6|0|0|j|m|f|5|0|y
    |8|0|2|e|@|o|0|6|j|0|0|7|x|b|a|0|l|s|0|0|0|r|w|0|5|p|3|0|0|0|h|z|0|t|0|q
    |0|t|g|b|r|y|q|s|0|5|d|x|7|0|e|0|4|0|f|a|m|l|u|6|v|w|2|8|h|0|0|0|n|9|p|0
    |0|a|u|w|0|z|1|m|o|n|l|p|q|d|f|0|i|t|0|0|2|0|0|4|r|0|9|y|j|k|0|0|x|0|0|v
    |7|0|m|0|q|i|0|c|0|0|r|f|9|j|h|5|0|8|t|p|v|d|g|1|x|e|0|l|n|4|s|o|a|3|0|u
    |0|8|1|m|7|0|e|0|0|d|0|0|0|l|k|z|5|h|0|b|0|6|n|3|4|v|0|s|0|@|0|f|0|0|a|0
    |a|z|6|0|0|x|n|8|0|q|o|0|r|u|d|9|j|4|0|w|0|0|@|p|0|0|0|h|c|y|0|t|0|0|s|0
    |0|b|0|3|p|5|u|0|i|y|0|9|0|0|s|n|a|2|x|0|0|c|8|g|q|f|0|t|z|j|d|7|v|0|l|0
    |y|0|s|0|j|0|0|4|0|b|2|k|3|v|t|x|w|c|z|o|0|9|5|0|i|0|8|0|r|l|n|e|p|1|0|h
    |d|0|k|2|t|v|z|j|0|h|0|@|0|o|0|0|e|0|l|s|f|m|r|0|p|3|x|a|b|0|8|g|9|w|u|0
    |e|r|o|0|0|0|l|7|x|a|s|c|0|f|0|0|0|g|k|0|t|v|h|d|0|5|w|0|u|6|2|j|@|z|q|0
    |i|u|0|a|b|0|0|0|0|p|@|m|s|k|c|0|0|9|0|x|n|0|6|o|7|2|j|0|0|0|0|8|g|e|t|5
    |0|1|0|h|0|k|b|i|n|0|0|0|d|g|0|0|x|3|2|q|u|@|j|e|o|t|f|0|y|0|0|p|s|4|w|0
    |0|3|0|g|o|@|r|1|6|7|u|j|0|y|4|0|t|0|d|0|p|z|l|5|n|8|s|b|0|c|a|k|0|0|f|m
    |6|7|t|0|0|w|s|f|3|c|9|0|0|0|@|e|1|5|0|0|0|b|k|0|u|x|0|0|p|0|0|0|j|n|o|0
    |0|0|0|l|y|j|w|0|0|g|k|0|p|6|0|2|r|u|a|f|7|3|t|v|h|q|@|m|1|0|i|d|z|0|0|9
    |0|m|x|z|0|f|5|0|d|0|0|2|0|i|0|7|0|l|0|r|8|0|9|0|0|6|4|0|w|0|u|3|h|q|1|@"""
      
    def toCell(x: String): Char = {
      val digits = ('0' to '9').toList
      x.charAt(0) match {
        case x if digits.contains(x) => digits.indexOf(x).toChar
        case x => x
      }
    }
    
    val solver = Solver(sudokutxt.split("\n").map(x => x.split("\\|").map(toCell).toList.filter(x => x != ' ')).toList)
    val ms66 = System.currentTimeMillis()
    println("Given:\n" + solver.givenGrid + "\n")  
    val s66 = solver.solve.toList
    println(s66.size + " Result(s) found in " + (System.currentTimeMillis() - ms66) + "ms:\n" + s66.mkString("\n"))
    
    assert(s66.size === 2)
    
  }
  test("baz") {
    println(Solver(List(
            List(0,4,0,0,0,0,0,7,0),
            List(8,0,0,9,4,6,0,0,3),
            List(0,0,6,7,0,2,5,0,0),
            List(0,3,2,0,0,0,9,6,0),
            List(0,9,0,0,0,0,0,4,0),
            List(0,6,1,0,0,0,8,3,0),
            List(0,0,4,6,0,5,2,0,0),
            List(1,0,0,2,8,4,0,0,6),
            List(0,2,0,0,0,0,0,1,0)
            )).solve.toList)           
    println(Solver(List(
            List(0,0,0,0,0,0,0,0,0),
            List(0,5,8,0,9,0,1,3,0),
            List(0,9,0,8,0,1,0,2,0),
            List(0,0,1,0,0,0,9,0,0),
            List(0,7,0,0,3,0,0,1,0),
            List(0,0,6,0,0,0,2,0,0),
            List(0,8,0,2,0,3,0,9,0),
            List(0,4,7,0,1,0,6,8,0),
            List(0,0,0,0,0,0,0,0,0)
            )).solve.toList)           
  }

}
