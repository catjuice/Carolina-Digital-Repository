package edu.unc.lib.dl.acl.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import edu.unc.lib.dl.xml.JDOMNamespaceUtil;

/**
 * These are the properties that the repository manages in the rels-ext datastream.
 *
 * @author count0
 *
 */
public enum UserRole {
	list("list", new Permission[] {}),
	accessCopiesPatron("access-copies-patron", new Permission[] {Permission.viewDescription, Permission.viewDerivative}),
	metadataPatron("metadata-patron", new Permission[] {Permission.viewDescription}),
	patron("patron", new Permission[] {Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal}),
	observer("observer", new Permission[] {Permission.viewAdminUI, Permission.viewEmbargoed, Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal}),
	ingester("ingester", new Permission[] {Permission.viewAdminUI, Permission.viewEmbargoed, Permission.addRemoveContents, Permission.editDescription, Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal}),
	processor("processor", new Permission[] {Permission.viewAdminUI, Permission.viewEmbargoed, Permission.addRemoveContents, Permission.publish, Permission.editDescription, Permission.moveToTrash, Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal}),
	curator("curator", new Permission[] {Permission.viewAdminUI, Permission.viewEmbargoed, Permission.addRemoveContents, Permission.publish, Permission.editDescription, Permission.moveToTrash, Permission.editAccessControl, Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal}),
	administrator("administrator", new Permission[] {Permission.viewAdminUI, Permission.viewEmbargoed, Permission.addRemoveContents, Permission.publish, Permission.editDescription, Permission.moveToTrash, Permission.editAccessControl, Permission.purgeForever, Permission.viewDescription, Permission.viewDerivative, Permission.viewOriginal});
	private URI uri;
	private String predicate;
	private Set<Permission> permissions;

	UserRole(String predicate, Permission[] perms) {
		try {
			this.predicate = predicate;
			this.uri = new URI(JDOMNamespaceUtil.CDR_ROLE_NS.getURI() + predicate);
			HashSet<Permission> mypermissions = new HashSet<Permission>(perms.length);
			Collections.addAll(mypermissions, perms);
			this.permissions = Collections.unmodifiableSet(mypermissions);
		} catch (URISyntaxException e) {
			Error x = new ExceptionInInitializerError("Cannot initialize ContentModelHelper");
			x.initCause(e);
			throw x;
		}
	}
	
	public static boolean matchesMemberURI(String test) {
		for(UserRole r : UserRole.values()) {
			if(r.getURI().toString().equals(test)) {
				return true;
			}
		}
		return false;
	}
	
	public static UserRole getUserRole(String roleUri) {
		for(UserRole r : UserRole.values()) {
			if(r.getURI().toString().equals(roleUri)) {
				return r;
			}
		}
		return null;
	}

	public URI getURI() {
		return this.uri;
	}
	
	public Set<Permission> getPermissions() {
		return permissions;
	}

	public String getPredicate() {
		return predicate;
	}

	public boolean equals(String value){
		return this.uri.toString().equals(value);
	}

	@Override
	public String toString() {
		return this.uri.toString();
	}
}