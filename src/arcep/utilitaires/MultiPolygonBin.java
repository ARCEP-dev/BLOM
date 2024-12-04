package arcep.utilitaires;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Cette classe est utilisée pour calculer efficacement si un point est dans un Polygon ou multipolygon.
 *
 * L'algorithme peut être vu ici :
 * https://erich.realtimerendering.com/ptinpoly/
 * 
 * Nous utilisons la "Bins Method" selon l'axe des y
 * 
 * Cette méthode à un coût d'initialisation mais est beaucoup plus rapide si on calcule pour de nombreux points
 *
 */
public class MultiPolygonBin {
	static int NbBucket = 1000;
	
	private final Envelope env;
	
	private final double minx;
	private final double maxx;
	private final double spread;
	
	private final List<LineString>[] bins;
	private final double[] minyBins;
	private final double[] maxyBins;
	
	private final GeometryFactory gf;

	
	@SuppressWarnings("unchecked")
	public MultiPolygonBin(Geometry geometry, GeometryFactory gf) {
    	env = geometry.getEnvelopeInternal();
    	minx = env.getMinX();
    	maxx = env.getMaxX();
    	
    	spread = maxx-minx;
    	this.gf = gf;
    	
		bins = new List[NbBucket];
		minyBins = new double[NbBucket];
		Arrays.fill(minyBins, Double.MAX_VALUE);
		maxyBins = new double[NbBucket];
        
    	for (var i = 0; i < NbBucket; i++) {
    		bins[i] = new ArrayList<>();
    	}
    	for (var i = 0; i < geometry.getNumGeometries(); i++) {
    		var polygon = (Polygon) geometry.getGeometryN(i);
    		fillBin(minx, spread, bins, polygon);
    	}
	}
	
	public boolean isIn(Point pt) {
		var bin = getBin(pt.getX());
		if (bin < 0 || bin >= NbBucket) {
			return false;
		}
		if (pt.getY() > maxyBins[bin]) {
			return false;
		}
		if (pt.getY() < minyBins[bin]) {
			return false;
		}
		var lines = bins[getBin(pt.getX())]; 
		int intersect = 0;

		for (var line : lines) {
			if (intersectsYaxis(line, pt)) {
				intersect++;
			}
		}

		// 1024285.01 6280813, 1024285.01 7062239.5
		return intersect % 2 == 1;
		
	}

	private boolean intersectsYaxis(LineString line, Point pt) {
		var coord1 = line.getCoordinateN(0);
		var coord2 = line.getCoordinateN(1);
		
		if (coord1.getX() == coord2.getX()) {
			return false; // parallele
		} 
		if (coord1.getX() < pt.getX()) {
			if (coord2.getX() < pt.getX()) return false;
		} else {
			if (coord2.getX() >= pt.getX()) return false;
		}
		
		// compute line Y value on pt.x axis
		var y = coord1.getY() + (pt.getX() - coord1.getX())/(coord2.getX() - coord1.getX()) * (coord2.getY() - coord1.getY());
		return y >= pt.getY();
	}

	private void fillBin(double minx, double spread, List<LineString>[] bins, Polygon polygon) {
		fillForOnePolygon(minx, spread, bins, polygon.getExteriorRing());
		for (var i = 0; i < polygon.getNumInteriorRing(); i++) {
			fillForOnePolygon(minx, spread, bins, polygon.getInteriorRingN(i));
		}
	}
	
	private int getBin(double x) {
		int bini = (int) (NbBucket * (x - minx) / spread);
		return bini == NbBucket ? NbBucket - 1 : bini;
	}

	private void fillForOnePolygon(double minx, double spread, List<LineString>[] bins, LinearRing line) {
		for (var j = 0; j < line.getNumPoints(); j++) {
			var point1 = line.getCoordinateN(j);
			var point2 = line.getCoordinateN((j+1) % line.getNumPoints());
			
			var minxPoints = Math.min(point1.x, point2.x);
			var maxxPoints = Math.max(point1.x, point2.x);
			var minyPoints = Math.min(point1.y, point2.y);
			var maxyPoints = Math.max(point1.y, point2.y);
			
			var lineString = gf.createLineString(new Coordinate[] {point1, point2});
			
			var from = getBin(minxPoints);
			var to = getBin(maxxPoints);
			
			for (int bini = from; bini <= to; bini++) {
				bins[bini].add(lineString);
				if (minyBins[bini] > minyPoints) {
					minyBins[bini] = minyPoints;
				}
				if (maxyBins[bini] < maxyPoints) {
					maxyBins[bini] = maxyPoints;
				}
			}
		}
	}
	
	boolean[][] isFullyIn;
	int gridSize;
	
	/**
	 * grid making
	 * with 
	 * a-b
	 * | |
	 * c-d
	 */
	public void buildGrid(int size) {
		isFullyIn = new boolean[size][size];
		gridSize = size;
		
		var minx = env.getMinX(); 
		var maxx = env.getMaxX(); 
		var miny = env.getMinY(); 
		var maxy = env.getMaxY();
		
		var incermentx = (maxx - minx)/size;
		var incrementy = (maxy - miny)/size;
		
		for (int i = 0; i < size; i++) {
			for (var j = 0; j < size; j++) {
				var a = gf.createPoint(new Coordinate(minx + i * incermentx, miny + j * incrementy));
				var b = gf.createPoint(new Coordinate(minx + i * incermentx, miny + (j+1) * incrementy));
				var c = gf.createPoint(new Coordinate(minx + (i+1) * incermentx, miny + j * incrementy));
				var d = gf.createPoint(new Coordinate(minx + (i+1) * incermentx, miny + (j+1) * incrementy));
				
				boolean isABfullyIn = isFullyIn(a,b);
				if (!isABfullyIn) {
					isFullyIn[i][j] = false;
					continue;
				}
				boolean isACfullyIn = isFullyIn(a,c);
				if (!isACfullyIn) {
					isFullyIn[i][j] = false;
					continue;
				}
				boolean isBDfullyIn = isFullyIn(b,d);
				if (!isBDfullyIn) {
					isFullyIn[i][j] = false;
					continue;
				}
				boolean isCDfullyIn = isFullyIn(c,d);
				if (!isCDfullyIn) {
					isFullyIn[i][j] = false;
					continue;
				}
				isFullyIn[i][j] = true;
			}
		}
	}

	private boolean isFullyIn(Point a, Point b) {
		var isAIn = isIn(a);
		if (!isAIn) {
			return false;
		}
		return !doesSegmentCross(a, b);
	}

	private boolean doesSegmentCross(Point a, Point b) {
		var startBin = getBin(Math.min(a.getX(), b.getX()));
		var endBin = getBin(Math.max(a.getX(), b.getX()));
		
		var lineAB = gf.createLineString(new Coordinate[] {a.getCoordinate(), b.getCoordinate()});
		
		for (var i = startBin; i <= endBin; i++) {
			for (var line : bins[i]) {
				if (lineAB.intersects(line)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isFullyIn(Envelope envelope) {
		var minx = env.getMinX(); 
		var maxx = env.getMaxX(); 
		var miny = env.getMinY(); 
		var maxy = env.getMaxY();
		
		var incrementx = (maxx - minx)/gridSize;
		var incrementy = (maxy - miny)/gridSize;

		var startx = (int)((envelope.getMinX() - minx) / incrementx); 
		var endx = (int)((envelope.getMaxX() - minx) / incrementx);
		
		if (startx < 0) startx = 0;
		if (endx >= gridSize) startx = gridSize - 1;
		
		var starty = (int)((envelope.getMinY() - miny) / incrementy); 
		var endy = (int)((envelope.getMaxY() - miny) / incrementy);
		
		if (starty < 0) starty = 0;
		if (endy >= gridSize) starty = gridSize - 1;
		
		for (int i = startx; i <= endx; i++ )
			for (int j = starty; j <= endy; j++ )
				if (!isFullyIn[i][j]) 
					return false;
		return true;
	}
}
