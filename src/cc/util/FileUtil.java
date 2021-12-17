/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cc.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 * @author aaron.cherney
 */
public abstract class FileUtil
{
	public static FileAttribute[] DIRPERS = new FileAttribute[]{PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ,
		PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, 
		PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, 
		PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE))};
	public static FileAttribute[] FILEPERS = new FileAttribute[]{PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, 
		PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ))};

	public static Set<StandardOpenOption> APPENDTO = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	public static Set<StandardOpenOption> WRITE = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	public static Set<StandardOpenOption> READ = EnumSet.of(StandardOpenOption.READ);
	
	
	public static OutputStream newOutputStream(Path oPath)
	   throws IOException
	{
		return newOutputStream(oPath, WRITE, FILEPERS);
	}
	
	
	public static OutputStream newOutputStream(Path oPath, Set<StandardOpenOption>oOpts, FileAttribute<?>... oAttrs)
	   throws IOException
	{
		return Channels.newOutputStream(Files.newByteChannel(oPath, oOpts, oAttrs));
	}
	
	
	public static InputStream newInputStream(Path oPath)
	   throws IOException
	{
		return Channels.newInputStream(Files.newByteChannel(oPath, READ));
	}
}
