use util;
use Time;

config const N = 150;
config const M = 100;
config const I = 42;   // maxiter
config const E = 0.1;  // convergence threshold
config const L = -100.0;
config const H = 100.0;
config const P = 1;
config const C = "/home/robin/Desktop/chap/pat2_150x100.pgm";                   //"/home/hphijma/images/pat1_150x100.pgm";
config const T = "/home/robin/Desktop/chap/plasma_150x100.pgm";  // "/home/hphijma/images/pat2_150x100.pgm";
config const help_params = false;
const M_SQRT2 = 1.41421356237309504880;

var max, min, average, MaxAvg : real = 0.0;

/* Add your code here */

const cn = 0.25 * M_SQRT2/(M_SQRT2+1) : real; 			//Coefficient for neighbors
const cd = 0.25 * 1/(M_SQRT2+1) : real ; 					   //Coefficient for diagonals

//const D: domain(2) = [2..N+1, 1..M];
//const ProblemSpace = {1..N, 1..M},    // domain for grid points
//BigDomain = {0..N+1, 1..M};   // domain including boundary points

const dom = {2..N+1, 1..M};

print_parameters();

 //var temp_tinit, tinit: [BigDomain] real = 0.0;  // declare arrays: 
                                        //   tinit stores approximate solution
                                        //   temp_tinit stores the next solution

var tinit_1: [1..(N+2), 1..M] real;                    // Temperature Matrix
readpgm_temp(T, N+2, M, {1..(N+2), 1..M}, tinit_1, L, H); 
var tinit_2: [1..(N+2), 1..M] real; 

const tcond: [1..N, 1..M] real;                   // Conductivity Matrix
readpgm_cond(C, N, M, {1..N, 1..M}, tcond, 0.0, 1.0);
var NaN:real;
proc do_MaxMinAvg( ){
  max = tinit_2[2,1]; min = tinit_2[2,1]; MaxAvg = abs(tinit_1[2,1] - tinit_2[2,1]);
 // writeln("max : ", max, " min :", min);
  for (i,j) in dom do{
  	//writeln("i: ",i," j : ",j);
  	//writeln("max : ", max, " min :", min);
  //	writeln("Max : ",max, " tinit_2[i,j] ", tinit_2[i,j], " i: ", i, " j: ",j);
    if(max < tinit_2[i,j]){ max = tinit_2[i,j]; }
    if(min > tinit_2[i,j]){ min = tinit_2[i,j]; }
    if(MaxAvg < abs(tinit_1[i,j] - tinit_2[i,j])){ MaxAvg = abs(tinit_1[i,j] - tinit_2[i,j]); }
   	//write(tinit_2[i,j]);
   	//assert(average == NaN);
    average = average + tinit_2[i,j];
  //  writeln("N : ", i, " M: ",j); writeln("average ", average);
  }
 // writeln("N : ", N, " M: ",M);
// writeln("avg: ",average);
  average = average/(N*M) : real;
 // writeln("average ", average);
  //writeln("max : ", max, " min :", min);
}


proc do_compute() {
  /* your main function */
  var r : results;
  var t : Timer;
  var iteration=0: int;
  var conductivity: real;
  var rest_conductivity: real;
  var lc: real; var rc: real;
  var rlc: real; var rrc: real;
  var flag=0:int;
  var delta= 0.0:real;

  t.start();
  tinit_2 = tinit_1;
 // writeln(tinit_2);
 // writeln(tinit_1);

 do{

  	  //Copy back

  	  tinit_1 = tinit_2;

	  //For common columns

	  for i in 2..(N+1){
		  	for j in 2..(M-1){

		  		conductivity = tcond[i-1,j];
		  		rest_conductivity = 1 - conductivity;

		  		tinit_2[i,j] = tinit_1[i,j]*conductivity +
		  		(tinit_1[i-1,j] + tinit_1[i+1, j] + tinit_1[i, j-1] + tinit_1[i, j+1] )*(cn * rest_conductivity) +
		  		(tinit_1[i+1,j-1] + tinit_1[i-1, j-1] + tinit_1[i-1, j+1] + tinit_1[i+1, j+1] )*(cd * rest_conductivity);

		  		//if(flag == 0 && abs(tinit_2[i,j] - tinit_1[i,j]) >= E ){
	        	//	flag = 1;
	        	//}
		  	}
		}

	  // For boundary Columns

	  var left=1:int; var right = M:int;
	  for i in 2..(N+1){

	  		lc = tcond[i-1, left];
	  		rlc = 1 - lc;
	
			// left column calculation	

	  		tinit_2[i,left] = tinit_1[i,left]*lc +
	  		(tinit_1[i-1,left] + tinit_1[i+1, left] + tinit_1[i, M] + tinit_1[i, left+1] )*(cn * rlc) +
	  		(tinit_1[i+1,M] + tinit_1[i-1, M] + tinit_1[i-1, left+1] + tinit_1[i+1, left+1] )*(cd * rlc);

	  		//if(flag == 0 && abs(tinit_2[i,left] - tinit_1[i,left]) >= E ){
	        //	flag = 1;
	        //}

	  		rc = tcond[i-1, right];
	  		rrc = 1 - rc;
	
	  		// right column calculation
	  		
	  		tinit_2[i,right] = tinit_1[i,right]*rc +
	  		(tinit_1[i-1,right] + tinit_1[i+1, right] + tinit_1[i, right-1] + tinit_1[i, 1] )*(cn * rrc) +
	  		(tinit_1[i+1,right-1] + tinit_1[i-1, right-1] + tinit_1[i-1, 1] + tinit_1[i+1, 1] )*(cd * rrc);

	  		//if(flag == 0 && abs(tinit_2[i,right] - tinit_1[i,right]) >= E ){
	        //	flag = 1;
	        //}
	  	}
	  		iteration+=1;
		  // check for the convergence
		   delta =  max reduce abs(tinit_2[dom] - tinit_1[dom]);
		  // writeln("tinit_2: ",tinit_2[110,1], "tinit_1: ",tinit_1[110,1]," iteration: ", iteration);
	   // if(flag == 0){
    	//	break;
    	//}
	      
	     

	  //writeln(tinit);
	 // writeln("iteration : ", iteration, "tinit[110,1] : ", tinit_2[110,1]);	
	 /* writeln("-------------------------------------------------- Iteration : ", iteration);
	   writeln("--------------------------------------------------tinit_1");
	  writeln(tinit_1);
	  writeln("--------------------------------------------------tinit_2");
      writeln(tinit_2); */
  }while( delta > E && iteration < I);
  
  //writeln(tinit_1);
  //writeln(tinit_2);
  do_MaxMinAvg( );
  
  //writeln(iteration);

  t.stop();

  r.tmin = min;
  r.tmax = max;
  r.maxdiff = MaxAvg;
  r.niter = iteration;
  r.tavg = average;
  r.time = t.elapsed();

  return r;
}

/* End add your code here */

util.main();