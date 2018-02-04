	/*
	 * Successive over relaxation
	 * (red-black SOR)
	 * SOR-PAR
		 * Written by 2591253
	 */

	#include <stdio.h>
	#include <stdlib.h>
	#include <math.h>
	#include <string.h>
	#include <sys/time.h>
	#include <mpi.h>

	#define TOLERANCE 0.00002       /* termination criterion */
	#define MAGIC 0.8               /* magic factor */

	#define MASTER 0 				// Taskid of first Task
	#define FROM_MASTER 1 			// Message type master -> worker
	#define FROM_WORKER 2  			// Message type worker -> master


	static int
	even(int i)
	{
	    return !(i & 1);
	}


	static double
	stencil(double **G, int row, int col)
	{
	    return (G[row - 1][col] + G[row + 1][col] +
	            G[row][col - 1] + G[row][col + 1]) / 4.0;
	}


	static void
	alloc_grid(double ***Gptr, int N)
	{
	    double    **G = malloc(N * sizeof *G);
	    if (G == NULL) {
	        fprintf(stderr, "malloc failed\n");
	        exit(42);
	    }

	    for (int i = 0; i < N; i++) {   /* malloc the own range plus one more line */
	        /* of overlap on each side */
	        G[i] = malloc(N * sizeof *G[i]);
	        if (G[i] == NULL) {
	            fprintf(stderr, "malloc failed\n");
	            exit(42);
	        }
	    }

	    *Gptr = G;
	}


	static void
	init_grid(double **G, int N)
	{
	    /* initialize the grid */
	    for (int i = 0; i < N; i++) {
	        for (int j = 0; j < N; j++) {
	            if (i == 0)
	                G[i][j] = 4.56;
	            else if (i == N - 1)
	                G[i][j] = 9.85;
	            else if (j == 0)
	                G[i][j] = 7.32;
	            else if (j == N - 1)
	                G[i][j] = 6.88;
	            else
	                G[i][j] = 0.0;
	        }
	    }
	}


	void
	print_grid(double **G, int N)
	{
	    for (int i = 1; i < N - 1; i++) {
	        for (int j = 1; j < N - 1; j++) {
	            printf("%10.3f ", G[i][j]);
	        }
	        printf("\n");
	    }
	}


	int
	main(int argc, char *argv[])
	{
	    int         N;              /* problem size */
	    int         ncol, nrow;     /* number of rows and columns */
	    double      Gnew;
	    double      r;
	    double      omega;
	    /* differences btw grid points in iters */
	    double      stopdiff;
	    double      maxdiff;
	    double      diff;
	    double    **G;              /* the grid */
	    int         iteration; /* counters */
	    struct timeval start;
	    struct timeval end;
	    double      time;
	    int         print = 0;

	    int portion; // number of rows master allocated to slave
	    int low_bound; // lower bound of row's allocated to slave
	    int upper_bound; // upper bound of row's allocated to slave
		int processorID;   // ID of thread
		int numPocessors;  // Number of workers
		int ret;
		MPI_Request request[4]; //capture request of a MPI_Isend
		MPI_Status status[4];


		ret = MPI_Init(&argc, &argv);  // initalize MPI operation

		if(ret != MPI_SUCCESS){
			perror("Error in MPI initialization");
			exit(EXIT_FAILURE);
		}

		ret = MPI_Comm_rank(MPI_COMM_WORLD, &processorID);  // Who am I 

		if (ret != MPI_SUCCESS) {
					perror("Rank of processors");
					exit(EXIT_FAILURE);
				}

		ret = MPI_Comm_size(MPI_COMM_WORLD, &numPocessors);  // Total number of Processors

		if (ret != MPI_SUCCESS) {
					perror("Total number of processors");
					exit(EXIT_FAILURE);
				}

		    /* set up problem size */
		    N = 1000;

		    for (int i = 1; i < argc; i++) {
		        if (strcmp(argv[i], "-print") == 0) {
		            print = 1;
		        } else {
		            if (sscanf(argv[i], "%d", &N) != 1) {
		                fprintf(stderr, "Positional parameter N must be an int, not '%s'\n",
		                        argv[i]);
		                exit(42);
		            }
		        }
		    }

		    N += 2;                     /* add the two border lines */
		    /* finally N*N (from argv) array points will be computed */

		    /* set up a quadratic grid */
		    ncol = nrow = N;
		    r = 0.5 * (cos(M_PI / ncol) + cos(M_PI / nrow));
		    omega = 2.0 / (1 + sqrt(1 - r * r));
		    stopdiff = TOLERANCE / (2.0 - omega);
		    omega *= MAGIC;


			    alloc_grid(&G, N);
			    init_grid(G, N);
	// Master initialize work
			if(processorID == 0){
				fprintf(stderr, "Running %d x %d SOR\n", N-2, N-2); 
			    if (gettimeofday(&start, 0) != 0) {
			        fprintf(stderr, "could not do timing\n");
			        exit(1);
			    }
		}
		    portion = ceil( (N-2) / numPocessors);  // use ceil for load balance

		    low_bound = (processorID * portion) + 1;
		    upper_bound = low_bound + portion - 1;

		    if(upper_bound > N){
		    	upper_bound = N;
		    }

		    if(low_bound > N){
		    	return 0;
		    }

	double      maxdiff_1;
	    /* now do the "real" computation */
	    iteration = 0;
	    do {
	        maxdiff = 0.0;
	             for (int phase = 0; phase < 2; phase++) {
	             	
	             	int times = 2;
	             	int i = low_bound;
	             	while(times--){
	             		for (int j = 1 + (even(i) ^ phase); j < N - 1; j += 2) {
		                    Gnew = stencil(G, i, j);
		                    diff = fabs(Gnew - G[i][j]);
		                    if (diff > maxdiff) {
		                        maxdiff = diff;
		                    }
		                    G[i][j] = G[i][j] + omega * (Gnew - G[i][j]);
	                    }
	                    i = processorID !=(numPocessors -1) ?  upper_bound :  N - 2;  // Assign i as last row 

	                    if(i == low_bound){   // N == 1
	             			break;
	             		}
	             	}
	             	
			        if(processorID != 0){
			        	MPI_Irecv((void *)G[low_bound-1], N, MPI_DOUBLE, (processorID -1), 0, MPI_COMM_WORLD, &request[0]);
			        	MPI_Isend(G[low_bound], N, MPI_DOUBLE, (processorID - 1), 0, MPI_COMM_WORLD, &request[1]);
			        }

			        if(processorID != ( numPocessors -1 )){
			        	MPI_Isend(G[upper_bound], N, MPI_DOUBLE, (processorID+1), 0, MPI_COMM_WORLD, &request[2]);
			        	MPI_Irecv((void *)G[upper_bound+1], N, MPI_DOUBLE, (processorID+1), 0, MPI_COMM_WORLD, &request[3]);
			        }
		   
		            for (int i = (low_bound + 1); processorID !=(numPocessors -1) ? i < upper_bound : i < N - 2; ++i) {
		                for (int j = 1 + (even(i) ^ phase); j < N - 1; j += 2) {
		                    Gnew = stencil(G, i, j);
		                    diff = fabs(Gnew - G[i][j]);
		                    if (diff > maxdiff) {
		                        maxdiff = diff;
		                    }
		                    G[i][j] = G[i][j] + omega * (Gnew - G[i][j]);
		                }
		            }

			        if(processorID != 0){
						 MPI_Wait( &request[0], &status[0] );
						 MPI_Wait(&request[1], &status[1]);
						}

					if(processorID != ( numPocessors -1 )){
					 	MPI_Wait( &request[2], &status[2] );
					    MPI_Wait( &request[3], &status[3] );
					}
		        }
			        // maxdiff collection from all the processors
			        MPI_Allreduce(&maxdiff, &maxdiff_1, 1, MPI_DOUBLE, MPI_MAX, MPI_COMM_WORLD);
			        iteration++;
		   } while (maxdiff_1 > stopdiff);

	    if(processorID == 0){
		    if (gettimeofday(&end, 0) != 0) {
		        fprintf(stderr, "could not do timing\n");
		        exit(1);
		    }

		    time = (end.tv_sec + (end.tv_usec / 1000000.0)) -
		        (start.tv_sec + (start.tv_usec / 1000000.0));

		    fprintf(stderr, "SOR took %10.3f seconds\n", time);

		    printf("Used %5d iterations, diff is %10.6f, allowed diff is %10.6f\n",
		           iteration, maxdiff_1, stopdiff);

		    if (print == 1) {
		    	for(int i =1; i< numPocessors;++i){
		    		int get_row=0;
		    		int bound = (i * portion) + 1;
		    		MPI_Recv(&get_row, 1, MPI_INT, i, 1, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
		    		while(get_row--){
		    			MPI_Recv((void *)G[bound],  N, MPI_DOUBLE, i, 1, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
		    			bound += 1;
		    		}
		    	}
		        print_grid(G, N);
		    }
		}

		if( processorID != 0 ){
			if(print == 1){
				upper_bound = (processorID !=(numPocessors -1) ? upper_bound :  N - 2);
				int rows = upper_bound - low_bound + 1; // number of rows to send to processor 0
				MPI_Send(&rows, 1 , MPI_INT, 0, 1, MPI_COMM_WORLD );
				for(int j = low_bound; processorID !=(numPocessors -1) ? j <= upper_bound : j <= N - 2; ++j){
					MPI_Send(G[j], N, MPI_DOUBLE, 0, 1, MPI_COMM_WORLD );
				}
			}
		}

		MPI_Finalize();
	    return 0;
	}
