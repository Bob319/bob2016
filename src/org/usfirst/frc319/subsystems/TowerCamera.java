// RobotBuilder Version: 2.0
//
// This file was generated by RobotBuilder. It contains sections of
// code that are automatically generated and assigned by robotbuilder.
// These sections will be updated in the future when you export to
// Java from RobotBuilder. Do not put any code or make any change in
// the blocks indicating autogenerated code or it will be lost on an
// update. Deleting the comments indicating the section will prevent
// it from being updated in the future.


package org.usfirst.frc319.subsystems;

import java.util.Comparator;
import java.util.Vector;

import org.usfirst.frc319.RobotMap;
import org.usfirst.frc319.commands.*;

import com.ni.vision.NIVision;
import com.ni.vision.NIVision.Image;
import com.ni.vision.NIVision.ImageType;

import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Subsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;


/**
 *
 */
public class TowerCamera extends Subsystem {
	
	private boolean running = false;

	//Images
	int session;
	Image frame;
	Image binaryFrame;
	int imaqError;
	
	//Final Constants
	private static double VIEW_ANGLE = 49.4; //View angle fo camera, set to Axis m1011 by default, 64 for m1013, 51.7 for 206, 52 for HD3000 square, 60 for HD3000 640x480
	
	//Flexible Constants
	private NIVision.Range RED_TARGET_R_RANGE = new NIVision.Range(100, 255);	//Default red range for the red target
	private NIVision.Range RED_TARGET_G_RANGE = new NIVision.Range(0, 255);		//Default green range for the red target
	private NIVision.Range RED_TARGET_B_RANGE = new NIVision.Range(0, 155);		//Default blue range for the red target
	
	private NIVision.Range BLU_TARGET_R_RANGE = new NIVision.Range(0, 155);		//Default red range for the blue target
	private NIVision.Range BLU_TARGET_G_RANGE = new NIVision.Range(0, 255);		//Default green range for the blue target
	private NIVision.Range BLU_TARGET_B_RANGE = new NIVision.Range(100, 255);	//Default blue range for the blue target
	
	private boolean RED_TEAM = false;
	private boolean BLU_TEAM = false;
	
	int MAX_PARTICLES = 5; //The maximum number of particles to iterate over
	double AREA_MINIMUM = 0.5; //Default Area minimum for particle as a percentage of total image area
	double SCORE_MIN = 75.0;  //Minimum score to be considered a target
	NIVision.ParticleFilterCriteria2 criteria[] = new NIVision.ParticleFilterCriteria2[1];
	NIVision.ParticleFilterOptions2 filterOptions = new NIVision.ParticleFilterOptions2(0,0,1,1);
	Scores scores = new Scores();
	
    // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTANTS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=CONSTANTS

    // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DECLARATIONS

    // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DECLARATIONS


    // Put methods for controlling this subsystem
    // here. Call these from Commands.

    public void initDefaultCommand() {
        // BEGIN AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DEFAULT_COMMAND

        setDefaultCommand(new PauseTowerCamera());

        // END AUTOGENERATED CODE, SOURCE=ROBOTBUILDER ID=DEFAULT_COMMAND

        // Set the default command for a subsystem here.
        // setDefaultCommand(new MySpecialCommand());
    }
    
    public void initialize(){
    	// create images
		frame = NIVision.imaqCreateImage(ImageType.IMAGE_RGB, 0);
		binaryFrame = NIVision.imaqCreateImage(ImageType.IMAGE_U8, 0);
		criteria[0] = new NIVision.ParticleFilterCriteria2(NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA, AREA_MINIMUM, 100.0, 0, 0);

		// the camera name (ex "cam0") can be found through the roborio web interface
        session = NIVision.IMAQdxOpenCamera("cam0",
                NIVision.IMAQdxCameraControlMode.CameraControlModeController);
		
		this.initializeDashboard();
    }
    
    public void run(){
    	
    	if(!running){
    		//if we are not currently running, start the camera
    		NIVision.IMAQdxStartAcquisition(session);
    	}
    	
    	/**
    	//read file in from disk. For this example to run you need to copy image.jpg from the SampleImages folder to the
		//directory shown below using FTP or SFTP: http://wpilib.screenstepslive.com/s/4485/m/24166/l/282299-roborio-ftp
		NIVision.imaqReadFile(frame, "/home/lvuser/SampleImages/image.jpg");
		**/
		
		//grab the latest image
		NIVision.IMAQdxGrab(session, frame, 1);

		this.readDashboard();
		
		if(BLU_TEAM == RED_TEAM){
			//MWT: THIS IS AN INCOMPATIBLE STATE, SOME ACTION SHOULD BE TAKEN
		}else if(BLU_TEAM){
			//Threshold the image looking for blue target
			NIVision.imaqColorThreshold(binaryFrame, frame, 255, NIVision.ColorMode.RGB, BLU_TARGET_R_RANGE, BLU_TARGET_G_RANGE, BLU_TARGET_B_RANGE);
		}else if(RED_TEAM){
			//Threshold the image looking for red target
			NIVision.imaqColorThreshold(binaryFrame, frame, 255, NIVision.ColorMode.RGB, RED_TARGET_R_RANGE, RED_TARGET_G_RANGE, RED_TARGET_B_RANGE);
		}else{
			//MWT: THIS IS AN INCOMPATIBLE STATE, SOME ACTION SHOULD BE TAKEN
		}
		

		//Send particle count to dashboard
		int numParticles = NIVision.imaqCountParticles(binaryFrame, 1);
		SmartDashboard.putNumber("Masked particles", numParticles);

		//Send masked image to dashboard to assist in tweaking mask.
		CameraServer.getInstance().setImage(binaryFrame);

		//filter out small particles 
		//MWT: IN 2014 WE USED A WIDTH FILTER INSTEAD OF AREA
		criteria[0].lower = (float)(AREA_MINIMUM);
		imaqError = NIVision.imaqParticleFilter4(binaryFrame, binaryFrame, criteria, filterOptions, null);

		//Send particle count after filtering to dashboard
		numParticles = NIVision.imaqCountParticles(binaryFrame, 1);
		SmartDashboard.putNumber("Filtered particles", numParticles);
		
		boolean foundTarget = false;
		double distance = 0d;

		if(numParticles > 0){
			//Measure particles and sort by particle size
			Vector<ParticleReport> particles = new Vector<ParticleReport>();
			//MWT: IN 2014 WE USED A MAX PARTICLE COUNT TO AVOID BOGGING DOWN THE CPU
			for(int particleIndex = 0;  particleIndex < MAX_PARTICLES && particleIndex < numParticles; particleIndex++){
				//MWT: IN 2014 WE USED AN ASPECT RATIO FILTER HERE
				ParticleReport par = new ParticleReport();
				par.PercentAreaToImageArea = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA_BY_IMAGE_AREA);
				par.Area = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_AREA);
				par.BoundingRectTop = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_TOP);
				par.BoundingRectLeft = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_LEFT);
				par.BoundingRectBottom = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_BOTTOM);
				par.BoundingRectRight = NIVision.imaqMeasureParticle(binaryFrame, particleIndex, 0, NIVision.MeasurementType.MT_BOUNDING_RECT_RIGHT);
				particles.add(par);
			}
			particles.sort(null);

			//MWT: IN 2014 WE EXPLICITLY DIDN'T USE THE SCORES MECHANISM
			
			//This example only scores the largest particle. Extending to score all particles and choosing the desired one is left as an exercise
			//for the reader. Note that this scores and reports information about a single particle (single U shaped target). To get accurate information 
			//you will need to evaluate the target against 1 or 2 other targets.
			scores.Aspect = getApspectScore(particles.elementAt(0));
			SmartDashboard.putNumber("Aspect", scores.Aspect);
			scores.Area = getAreaScore(particles.elementAt(0));
			SmartDashboard.putNumber("Area", scores.Area);
			
			foundTarget= scores.Aspect > SCORE_MIN && scores.Area > SCORE_MIN;
			distance = computeDistance(binaryFrame, particles.elementAt(0));
		}
		
		//MWT: IDEALLY WE WOULD PUT A GREEN AROUND THE IMAGE WHEN THE TARGET IS FOUND AND RED WHEN IT IS NOT
		//Send distance and target status to dashboard. The bounding rect, particularly the horizontal center (left - right) may be useful for rotating/driving towards a target
		SmartDashboard.putBoolean("Found Target", foundTarget);
		SmartDashboard.putNumber("Distance", distance);
    }
    
    //MWT: THIS IS NEVER REFERENCED
	//Comparator function for sorting particles. Returns true if particle 1 is larger
  	static boolean compareParticleSizes(ParticleReport particle1, ParticleReport particle2)
  	{
  		//we want descending sort order
  		return particle1.PercentAreaToImageArea > particle2.PercentAreaToImageArea;
  	}

  	/**
  	 * Converts a ratio with ideal value of 1 to a score. The resulting function is piecewise
  	 * linear going from (0,0) to (1,100) to (2,0) and is 0 for all inputs outside the range 0-2
  	 */
  	double ratioToScore(double ratio)
  	{
  		return (Math.max(0, Math.min(100*(1-Math.abs(1-ratio)), 100)));
  	}

  	double getAreaScore(ParticleReport report)
  	{
  		double boundingArea = (report.BoundingRectBottom - report.BoundingRectTop) * (report.BoundingRectRight - report.BoundingRectLeft);
  		//Tape is 7" edge so 49" bounding rect. With 2" wide tape it covers 24" of the rect.
  		return ratioToScore((49/24)*report.Area/boundingArea);
  	}

  	/**
  	 * Method to score if the aspect ratio of the particle appears to match the retro-reflective target. Target is 7"x7" so aspect should be 1
  	 */
  	double getApspectScore(ParticleReport report)
  	{
  		return ratioToScore(((report.BoundingRectRight-report.BoundingRectLeft)/(report.BoundingRectBottom-report.BoundingRectTop)));
  	}

  	/**
  	 * Computes the estimated distance to a target using the width of the particle in the image. For more information and graphics
  	 * showing the math behind this approach see the Vision Processing section of the ScreenStepsLive documentation.
  	 *
  	 * @param image The image to use for measuring the particle estimated rectangle
  	 * @param report The Particle Analysis Report for the particle
  	 * @return The estimated distance to the target in feet.
  	 */
  	double computeDistance (Image image, ParticleReport report) {
  		double normalizedWidth, targetWidth;
  		NIVision.GetImageSizeResult size;

  		size = NIVision.imaqGetImageSize(image);
  		normalizedWidth = 2*(report.BoundingRectRight - report.BoundingRectLeft)/size.width;
  		targetWidth = 7;

  		return  targetWidth/(normalizedWidth*12*Math.tan(VIEW_ANGLE*Math.PI/(180*2)));
  	}
  	
  	private void initializeDashboard(){
  		//Put default values to SmartDashboard so fields will appear
  		SmartDashboard.putNumber("RED TARGET (R min)", RED_TARGET_R_RANGE.minValue);
  		SmartDashboard.putNumber("RED TARGET (R max)", RED_TARGET_R_RANGE.maxValue);
  		SmartDashboard.putNumber("RED TARGET (G min)", RED_TARGET_G_RANGE.minValue);
  		SmartDashboard.putNumber("RED TARGET (G max)", RED_TARGET_G_RANGE.maxValue);
  		SmartDashboard.putNumber("RED TARGET (B min)", RED_TARGET_B_RANGE.minValue);
  		SmartDashboard.putNumber("RED TARGET (B max)", RED_TARGET_B_RANGE.maxValue);
  		
  		SmartDashboard.putNumber("BLUE TARGET (R min)", BLU_TARGET_R_RANGE.minValue);
  		SmartDashboard.putNumber("BLUE TARGET (R max)", BLU_TARGET_R_RANGE.maxValue);
  		SmartDashboard.putNumber("BLUE TARGET (G min)", BLU_TARGET_G_RANGE.minValue);
  		SmartDashboard.putNumber("BLUE TARGET (G max)", BLU_TARGET_G_RANGE.maxValue);
  		SmartDashboard.putNumber("BLUE TARGET (B min)", BLU_TARGET_B_RANGE.minValue);
  		SmartDashboard.putNumber("BLUE TARGET (B max)", BLU_TARGET_B_RANGE.maxValue);
  		
  		SmartDashboard.putBoolean("RED TEAM", RED_TEAM);
  		SmartDashboard.putBoolean("BLUE TEAM", BLU_TEAM);
  		
  		SmartDashboard.putNumber("AREA MIN %", AREA_MINIMUM);
  	}
  	
  	private void readDashboard(){
  		//Update threshold values from SmartDashboard. For performance reasons it is recommended to remove this after calibration is finished.
  		RED_TARGET_R_RANGE.minValue = (int)SmartDashboard.getNumber("RED TARGET (R min)", RED_TARGET_R_RANGE.minValue);
  		RED_TARGET_R_RANGE.maxValue = (int)SmartDashboard.getNumber("RED TARGET (R max)", RED_TARGET_R_RANGE.maxValue);
  		RED_TARGET_G_RANGE.minValue = (int)SmartDashboard.getNumber("RED TARGET (G min)", RED_TARGET_G_RANGE.minValue);
  		RED_TARGET_G_RANGE.maxValue = (int)SmartDashboard.getNumber("RED TARGET (G max)", RED_TARGET_G_RANGE.maxValue);
  		RED_TARGET_B_RANGE.minValue = (int)SmartDashboard.getNumber("RED TARGET (B min)", RED_TARGET_B_RANGE.minValue);
  		RED_TARGET_B_RANGE.maxValue = (int)SmartDashboard.getNumber("RED TARGET (B max)", RED_TARGET_B_RANGE.maxValue);
  		
  		BLU_TARGET_R_RANGE.minValue = (int)SmartDashboard.getNumber("BLUE TARGET (R min)", BLU_TARGET_R_RANGE.minValue);
  		BLU_TARGET_R_RANGE.maxValue = (int)SmartDashboard.getNumber("BLUE TARGET (R max)", BLU_TARGET_R_RANGE.maxValue);
  		BLU_TARGET_G_RANGE.minValue = (int)SmartDashboard.getNumber("BLUE TARGET (G min)", BLU_TARGET_G_RANGE.minValue);
  		BLU_TARGET_G_RANGE.maxValue = (int)SmartDashboard.getNumber("BLUE TARGET (G max)", BLU_TARGET_G_RANGE.maxValue);
  		BLU_TARGET_B_RANGE.minValue = (int)SmartDashboard.getNumber("BLUE TARGET (B min)", BLU_TARGET_B_RANGE.minValue);
  		BLU_TARGET_B_RANGE.maxValue = (int)SmartDashboard.getNumber("BLUE TARGET (B max)", BLU_TARGET_B_RANGE.maxValue);
  	
  		RED_TEAM = SmartDashboard.getBoolean("RED TEAM", RED_TEAM);
  		BLU_TEAM = SmartDashboard.getBoolean("BLUE TEAM", BLU_TEAM);
  		
  		AREA_MINIMUM = SmartDashboard.getNumber("AREA MIN %", AREA_MINIMUM);
  	}
    
  	//A structure to hold measurements of a particle
  	public class ParticleReport implements Comparator<ParticleReport>, Comparable<ParticleReport>{
  		double PercentAreaToImageArea;
  		double Area;
  		double BoundingRectLeft;
  		double BoundingRectTop;
  		double BoundingRectRight;
  		double BoundingRectBottom;
  		
  		public int compareTo(ParticleReport r)
  		{
  			return (int)(r.Area - this.Area);
  		}
  		
  		public int compare(ParticleReport r1, ParticleReport r2)
  		{
  			return (int)(r1.Area - r2.Area);
  		}
  	};
  	
  	//Structure to represent the scores for the various tests used for target identification
  	public class Scores {
  		double Area;
  		double Aspect;
  	};
}

