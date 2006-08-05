/*
 * Phys2D - a 2D physics engine based on the work of Erin Catto. The
 * original source remains:
 * 
 * Copyright (c) 2006 Erin Catto http://www.gphysics.com
 * 
 * This source is provided under the terms of the BSD License.
 * 
 * Copyright (c) 2006, Phys2D
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or 
 * without modification, are permitted provided that the following 
 * conditions are met:
 * 
 *  * Redistributions of source code must retain the above 
 *    copyright notice, this list of conditions and the 
 *    following disclaimer.
 *  * Redistributions in binary form must reproduce the above 
 *    copyright notice, this list of conditions and the following 
 *    disclaimer in the documentation and/or other materials provided 
 *    with the distribution.
 *  * Neither the name of the Phys2D/New Dawn Software nor the names of 
 *    its contributors may be used to endorse or promote products 
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS 
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY 
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR 
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE.
 */
package net.phys2d.raw;

import net.phys2d.math.MathUtil;
import net.phys2d.math.Vector2f;

/**
 * A arbiter resolving contacts between a pair of bodies
 * 
 * @author Kevin Glass
 */
public strictfp class Arbiter {
	/** The maximum number of points of contact */
	public static final int MAX_POINTS = 2;

	/** The contacts being resolved by this arbiter */
	private Contact[] contacts = new Contact[MAX_POINTS];
	/** The number of contacts made */
	private int numContacts;
	/** The first body in contact */
	private Body body1;
	/** The second body in contact */
	private Body body2;
	/** Combined friction between two bodies */
	private float friction;
	
	/**
	 * Create a new arbiter - this should only be done by the 
	 * engine
	 * 
	 * @param b1 The first body in contact
	 * @param b2 The second body in contact
	 */
	Arbiter(Body b1, Body b2) {
		for (int i=0;i<MAX_POINTS;i++) {
			contacts[i] = new Contact();
		}
		
		if (b1.hashCode() < b2.hashCode())
		{
			body1 = b1;
			body2 = b2;
		}
		else
		{
			body1 = b2;
			body2 = b1;
		}
	}
	
	/**
	 * Perform the collision analysis between the two bodies 
	 * arbitrated
	 */
	public void collide() {
		numContacts = Collide.collide(contacts, body1, body2);
	} 
	
	/**
	 * Initialise state for this arbiter - this is only done 
	 * once per pair of bodies. It's used to caculated static
	 * data between them
	 *
	 */
	public void init() {
		if (numContacts > 0) {
			friction = (float) Math.sqrt(body1.getFriction() * body2.getFriction());
		}
	}

	/**
	 * Retrieve the contacts being resolved by this arbiter
	 * 
	 * @return The contacts being resolved by this arbiter
	 */
	public Contact[] getContacts() {
		return contacts;
	}
	
	/**
	 * The number of contacts being resolved by this arbiter
	 * 
	 * @return The number of contacts being resolve by this arbiter
	 */
	public int getNumContacts() {
		return numContacts;
	}
	
	/**
	 * Update this arbiter from a second set of data determined
	 * as the simulation continues
	 * 
	 * @param newContacts The new contacts that have been found
	 * @param numNewContacts The number of new contacts discovered
	 */
	void update(Contact[] newContacts, int numNewContacts) {
		Contact[] mergedContacts = new Contact[] {new Contact(), new Contact()};
		
		for (int i = 0; i < numNewContacts; ++i)
		{
			Contact cNew = newContacts[i];
			int k = -1;
			for (int j = 0; j < numContacts; ++j)
			{
				Contact cOld = contacts[j];
				if (cNew.feature.equals(cOld.feature))
				{
					k = j;
					break;
				}
			}

			if (k > -1)
			{
				Contact c = mergedContacts[i];
				Contact cOld = contacts[k];
				c.set(cNew);
				c.accumulatedNormalImpulse = cOld.accumulatedNormalImpulse;
				c.accumulatedTangentImpulse = cOld.accumulatedTangentImpulse;
			}
			else
			{
				mergedContacts[i].set(newContacts[i]);
			}
		}

		for (int i = 0; i < numNewContacts; ++i) {
			contacts[i].set(mergedContacts[i]);
		}

		numContacts = numNewContacts;
	}

	/**
	 * Apply the friction impulse from each contact.
	 * 
	 * @param invDT The amount of time to step the simulation by
	 */
	void preStep(float invDT) {
		final float k_allowedPenetration = 0.01f;

		for (int i = 0; i < numContacts; ++i)
		{
			Contact c = contacts[i];
			
			Vector2f r1 = new Vector2f(c.position);
			r1.sub(body1.getPosition());
			Vector2f r2 = new Vector2f(c.position);
			r2.sub(body2.getPosition());

			// Precompute normal mass, tangent mass, and bias.
			float rn1 = r1.dot(c.normal);
			float rn2 = r2.dot(c.normal);
			float kNormal = body1.getInvMass() + body2.getInvMass();
			kNormal += body1.getInvI() * (r1.dot(r1) - rn1 * rn1) + body2.getInvI() * (r2.dot(r2) - rn2 * rn2);
			c.massNormal = 1.0f / kNormal;
			
			Vector2f tangent = MathUtil.cross(1.0f, c.normal);
			float rt1 = r1.dot(tangent);
			float rt2 = r2.dot(tangent);
			float kTangent = body1.getInvMass() + body2.getInvMass();
			kTangent += body1.getInvI() * (r1.dot(r1) - rt1 * rt1) + body2.getInvI() * (r2.dot(r2) - rt2 * rt2);
			c.massTangent = 1.0f /  kTangent;

			// TODO: This hard code 0.1 is probably because of the standard 10 iterations
			// i.e. 1/10 - this probably isn't right.
			c.bias = -0.1f * invDT * Math.min(0.0f, c.separation + k_allowedPenetration);
			
			// Apply normal + friction impulse
			Vector2f impulse = MathUtil.scale(c.normal, c.accumulatedNormalImpulse);
			impulse.add(MathUtil.scale(tangent, c.accumulatedTangentImpulse));

			impulse.scale(body1.getHardness() + body2.getHardness());
			body1.adjustVelocity(MathUtil.scale(impulse, -body1.getInvMass()));
			body1.adjustAngularVelocity(-body1.getInvI() * MathUtil.cross(r1, impulse));

			body2.adjustVelocity(MathUtil.scale(impulse, body2.getInvMass()));
			body2.adjustAngularVelocity(body2.getInvI() * MathUtil.cross(r2, impulse));
		}
	}
	
	/**
	 * Apply the impulse accumlated at the contact points maintained
	 * by this arbiter.
	 */
	void applyImpulse() {
		Body b1 = body1;
		Body b2 = body2;

		for (int i = 0; i < numContacts; ++i)
		{
			Contact c = contacts[i];
			
			Vector2f r1 = new Vector2f(c.position);
			r1.sub(b1.getPosition());
			Vector2f r2 = new Vector2f(c.position);
			r2.sub(b2.getPosition());

			// Relative velocity at contact
			Vector2f dv =  new Vector2f(b2.getVelocity());
			dv.add(MathUtil.cross(b2.getAngularVelocity(), r2));
			dv.sub(b1.getVelocity());
			dv.sub(MathUtil.cross(b1.getAngularVelocity(),r1));
			
			// Compute normal impulse with bias.
			float vn = dv.dot(c.normal);
			float normalImpulse = c.massNormal * (-vn + c.bias);
			// Clamp the accumulated impulse
			float oldNormalImpulse = c.accumulatedNormalImpulse;
			c.accumulatedNormalImpulse = Math.max(oldNormalImpulse + normalImpulse, 0.0f);
			normalImpulse = c.accumulatedNormalImpulse - oldNormalImpulse;

			// Apply contact impulse
			Vector2f impulse = MathUtil.scale(c.normal, normalImpulse);

			b1.adjustVelocity(MathUtil.scale(impulse, -b1.getInvMass()));
			b1.adjustAngularVelocity(-(b1.getInvI() * MathUtil.cross(r1, impulse)));

			b2.adjustVelocity(MathUtil.scale(impulse, b2.getInvMass()));
			b2.adjustAngularVelocity(b2.getInvI() * MathUtil.cross(r2, impulse));
			
			// Relative velocity at contact
			dv =  new Vector2f(b2.getVelocity());
			dv.add(MathUtil.cross(b2.getAngularVelocity(), r2));
			dv.sub(b1.getVelocity());
			dv.sub(MathUtil.cross(b1.getAngularVelocity(),r1));
			
			// Compute friction impulse
			float maxTangentImpulse = friction * c.accumulatedNormalImpulse;

			Vector2f tangent = MathUtil.cross(1.0f, c.normal);
			float vt = dv.dot(tangent);
			float tangentImpulse = c.massTangent * (-vt);

			// Clamp friction
			float oldTangentImpulse = c.accumulatedTangentImpulse;
			c.accumulatedTangentImpulse = MathUtil.clamp(oldTangentImpulse + tangentImpulse, -maxTangentImpulse, maxTangentImpulse);
			tangentImpulse = c.accumulatedTangentImpulse - oldTangentImpulse;

			// Apply contact impulse
			impulse = MathUtil.scale(tangent, tangentImpulse);

			b1.adjustVelocity(MathUtil.scale(impulse, -b1.getInvMass()));
			b1.adjustAngularVelocity(-b1.getInvI() * MathUtil.cross(r1, impulse));

			b2.adjustVelocity(MathUtil.scale(impulse, b2.getInvMass()));
			b2.adjustAngularVelocity(b2.getInvI() * MathUtil.cross(r2, impulse));
		}
	}
	
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return body1.hashCode()+body2.hashCode();
	}
	
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (other.getClass().equals(getClass())) {
			Arbiter o = (Arbiter) other;
			
			return (o.body1.equals(body1) && o.body2.equals(body2));
		}
		
		return false;
	}
}