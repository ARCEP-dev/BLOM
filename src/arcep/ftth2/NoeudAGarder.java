/*
 * Copyright (c) 2025, Autorité de régulation des communications électroniques, des postes et de la distribution de la presse
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package arcep.ftth2;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/*
* Un NoeudAGarder est un noeud extrémité d'une arête dans le graphe représentant
* le Reseau desservi par un NRO
*/
public class NoeudAGarder extends Node {
	public static class MeilleurChemin {
	    public double distanceAuCentre = Double.POSITIVE_INFINITY;
		public NoeudAGarder noeudPrecedent;
		public Arete aretePrecedente;
	}
	
    boolean pathConnu;

    private List<Long> pathToCentre;
    private Map<String,Integer> demandeParZone;
    boolean indicePMint;

    public double distanceAuCentre;
    public MeilleurChemin meilleurChemin = new MeilleurChemin();


    public NoeudAGarder(){}

    public NoeudAGarder(int id, double[] coord){
        this.id = id;
        this.coord = coord;
        pathConnu = false;
        demandeParZone = new HashMap<>();
        indicePMint = false;
    }

    public void init(){
        demandeParZone = new HashMap<>();
        pathToCentre = new ArrayList<>();
    }

    public void declarePMint(){
        this.indicePMint = true;
    }

    public void addDemande(String zone, int nbLignes){
        if (!demandeParZone.containsKey(zone)) demandeParZone.put(zone, nbLignes);
        else demandeParZone.put(zone,demandeParZone.get(zone)+nbLignes);
    }

    public boolean hasDemandeLocale(){
        return demandeParZone.size() > 0;
    }
    
    public void print(PrintWriter writer){
        writer.print(id+";"+coord[0]+";"+coord[1]+";"+demandeParZone.size());
        for (String zone : demandeParZone.keySet()){
            writer.print(";"+zone+";"+(int) demandeParZone.get(zone));
        }
        int isPMint = 0;
        if (this.indicePMint || this.getDemandeLocale("ZTD_HD") >= 12)
            isPMint = 1;
        writer.print(";"+isPMint);
        if (this.hasDemandeLocale()){
        	var pathToCentre = getPathFromMeilleurResultat();
        	
            writer.print(";"+pathToCentre.size());
            for (Long n : pathToCentre){
                writer.print(";"+n);
            }
        } else writer.print(";0");
        writer.println();
    }

	public List<Arete> getAretePathFromMeilleurResultat() {
		var pathToCentre = new LinkedList<Arete>();
		var current = this;
		while(current != null) {
			if (current.meilleurChemin.aretePrecedente == null) {
				break;
			}
			if (current.meilleurChemin.aretePrecedente == null) {
				throw new RuntimeException("To investigate");
			}
			pathToCentre.add(0, current.meilleurChemin.aretePrecedente);
			current = current.meilleurChemin.noeudPrecedent;        				
		}
		return pathToCentre;
	}

	public List<Long> getPathFromMeilleurResultat() {
		var pathToCentre = new LinkedList<Long>();
		var current = this;
		while(current != null) {
			if (current.meilleurChemin.aretePrecedente == null) {
				break;
			}
			if (current.meilleurChemin.aretePrecedente == null) {
				throw new RuntimeException("To investigate");
			}
			pathToCentre.add(0, current.meilleurChemin.aretePrecedente.id);
			current = current.meilleurChemin.noeudPrecedent;        				
		}
		return pathToCentre;
	}

    public void setPath(List<Long> ids){
        pathToCentre = ids;
    }
    public void addToPath(Long id){
        pathToCentre.add(id);
    }

    public List<Long> getPath(){
        return pathToCentre;
    }

    public int getDemandeLocale(String zone){
        if (this.demandeParZone.containsKey(zone))
            return this.demandeParZone.get(zone);
        else
            return 0;
    }

    public int demandeLocaleTotale(){
        int demande = 0;
        for (String zone : demandeParZone.keySet()){
            demande+= demandeParZone.get(zone);
        }
        return demande;
    }

    public void modifieDemande(String zone, int n){
        this.demandeParZone.put(zone, this.demandeParZone.get(zone)+n);
    }
    
    /**
     * Fonction permettant d'initialiser l'objet FeatureType pour le tracé
     * des shapefiles des NoeudAGarder
     * 
     * @param crs (CoordinateReferenceSystem) Système de coordonnées de référence pour l'écriture du shapefile
     * @return SimpleFeatureType spécifiant les attributs pour le tracé du NoeudAGarder
     */
    public static SimpleFeatureType getFeatureType(CoordinateReferenceSystem crs){
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Vertex");
        builder.setCRS(crs);
        builder.add("the_geom", Point.class);
        builder.add("ID", Integer.class);
        builder.length(13).add("ID_RSO", String.class);
        return builder.buildFeatureType();
    }
    
    /**
     * Fonction permettant de créer la Feature correspondant au tracé d'un NoeudAGarder
     * 
     * @param idReseau (String) Nom du NRO / zone
     * @param gf
     * @param featureBuilder SimpleFeatureBuilder permettant le tracé de la SimpleFeature
     * @return SimpleFeature représentant le tracé du NoeudAGarder
     */
    public SimpleFeature getFeature(String idReseau, GeometryFactory gf, SimpleFeatureBuilder featureBuilder){
        featureBuilder.add(gf.createPoint(new Coordinate(this.coord[0], this.coord[1])));
        featureBuilder.add(this.id);
        featureBuilder.add(idReseau);
        return featureBuilder.buildFeature(null);
    }
    
    @Override
    public String toString() {
    	return String.format("[%f,%f]", coord[0], coord[1]);
    }
    
}
