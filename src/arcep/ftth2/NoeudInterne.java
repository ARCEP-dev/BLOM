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

/*
* Un NoeudInterne est un noeud intermédiaire sur l'arête
* reliant deux NoeudAGarder
* Un NoeudInterne peut devenir un NoeudAGarder s'il est le noeud qui minimise
* le plus court chemin NRO-PC d'un PC ; la méthode actualise permet alors de
* modifier les NoeudInterne voisins de celui qui est devenu NoeudAGarder
*/
public class NoeudInterne extends Node{
    
    Arete a;
    NoeudAGarder[] voisins = new NoeudAGarder[2];
    double distance;
    int indice;

    public NoeudInterne(int id, double[] coord, NoeudAGarder n1, NoeudAGarder n2, Arete a, double distance, int indice){
        this.id = id;
        this.coord = coord;
        this.a = a;
        voisins[0] = n1;
        voisins[1] = n2;
        this.distance = distance;
        this.indice = indice;
    }

    public void actualise(Arete a, NoeudAGarder n1, NoeudAGarder n2, double distance){
        this.a = a;
        this.voisins[0] = n1;
        this.voisins[1] = n2;
        this.distance = distance;
    }
}
