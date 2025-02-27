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

public class PC extends PointReseau {
    
    String nra;
    int lignes;
    long parcelle = 0;
    boolean PM_int;
    String changementSR;

    public PC() {}

    public PC(String identifiant, double x, double y, PointReseau pere, String nra, int lignes, String zone, boolean galerieVisitable) {
        // champs hérités de PointReseau
        this.init(identifiant, x, y, "PC", zone);
        this.pere = pere;
        this.changementNRA = "FAUX";
        
        //champs propres
        this.nra = nra;
        this.lignes = lignes;
        
        //this.parcelle = parcelle;
        this.changementSR = "FAUX";
        this.PM_int = zone.equals("ZTD_HD") && (galerieVisitable || lignes >= 12); 
    }

    public void combinaison(PC pr) {
        int sommeLignes = this.lignes + pr.lignes;
        this.x = (this.x*this.lignes + pr.x*pr.lignes)/sommeLignes;
        this.y = (this.y*this.lignes + pr.y*pr.lignes)/sommeLignes;
        this.lignes = sommeLignes;
    }

}
