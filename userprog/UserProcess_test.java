package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.*;

/*Initial Version with system call*/

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		UserKernel.pIDMutex.P();
		this.pID = UserKernel.pID++;
		UserKernel.pIDMutex.V();

		fileArray[0] = UserKernel.console.openForReading();
		Lib.assertTrue(fileArray[0] != null);	
		fileArray[1] = UserKernel.console.openForWriting();
		Lib.assertTrue(fileArray[1] != null);

		for (int i = 2; i < MAX_FILE; ++i){
			fileArray[i] = null;
		}
		fileNum = 2;
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	public void setParent(int pid, UserProcess par){
		this.parentPID = pid;
		this.parentProcess = par;
	}

	public int getPID(){
		return this.pID;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, vpn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	private int findEmptyDescriptor(){
		for (int i = 2; i < MAX_FILE; ++i){
			if (fileArray[i] == null)
				return i;
		}
		return -1;
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		if (this != UserKernel.root){
			Lib.debug(dbgProcess, "Halting a non-root process");
			return -1;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}


	/*Handle the creat() system call.*/
	private int handleCreate(int vaddr) {
		if (fileNum >= MAX_FILE){
			Lib.debug(dbgProcess, "File array is full!");
			return -1;
		}

		String fileName = readVirtualMemoryString(vaddr, 256);
		if (fileName == null){
			Lib.debug(dbgProcess, "File name Illegal!");
			return -1;
		}
		OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, true);
		if (newFile == null){
			Lib.debug(dbgProcess, "Cannot create file!");
			return -1;
		}
		fileNum++;
		int descriptor = findEmptyDescriptor();
		if (descriptor >= 2 && descriptor < MAX_FILE)
			fileArray[descriptor] = newFile;
		return descriptor;
	}


	private int handleOpen(int vaddr){
		if (fileNum >= MAX_FILE){
			Lib.debug(dbgProcess, "Exceed maximum descriptor/x!");
			return -1;
		}

		String fileName = readVirtualMemoryString(vaddr, 256);
		OpenFile newFile = null;
		if (fileName == null){
			Lib.debug(dbgProcess, "File name Illegal!");
			return -1;
		}
		Lib.debug(dbgProcess, "Open file name: " + fileName);
		newFile = ThreadedKernel.fileSystem.open(fileName, false);
		if (newFile == null){
			Lib.debug(dbgProcess, "Cannot access file!");
			return -1;
		}
		fileNum++;
		int descriptor = findEmptyDescriptor();
		if (descriptor >= 2 && descriptor < MAX_FILE)
			fileArray[descriptor] = newFile;
		return descriptor;
	}


	private int handleRead(int fd, int vaddr, int count){
		if (fd >= MAX_FILE || fd < 0 || fd == 1 || fileArray[fd] == null){
			Lib.debug(dbgProcess, "Illegal file descriptor!");
			return -1;
		}
		if (count < 0){
			Lib.debug(dbgProcess, "Byte count is negative!");
		}

		byte[] buffer = new byte[pageSize];

		int readCnt = 0;
		while(readCnt < count){
			int curCnt = 0, writeCnt = 0;
			if (count - readCnt >= pageSize){
				curCnt = fileArray[fd].read(buffer, 0, pageSize);
				if (curCnt < 0){
					Lib.debug(dbgProcess, "Read error!");
					return -1;
				}
				writeCnt = this.writeVirtualMemory(vaddr, buffer, 0, curCnt);
				if (writeCnt < curCnt){
					Lib.debug(dbgProcess, "Read and write do not match!");
					return -1;
				}
				readCnt += curCnt;
				vaddr += curCnt;
				if (curCnt < pageSize)
					break;
			}else{
				curCnt = fileArray[fd].read(buffer, 0, count - readCnt);
				if (curCnt < 0){
					Lib.debug(dbgProcess, "Read error!");
					return -1;
				}
				writeCnt = this.writeVirtualMemory(vaddr, buffer, 0, curCnt);
				if (writeCnt < curCnt){
					Lib.debug(dbgProcess, "Read and write do not match!");
					return -1;
				}
				readCnt += curCnt;
				vaddr += curCnt;
				if (readCnt < count)
					break;
			}
		}

		return readCnt;

	}

	private int handleWrite(int fd, int vaddr, int count){
		if (fd >= MAX_FILE || fd <= 0 || fileArray[fd] == null){
			Lib.debug(dbgProcess, "Illegal file descriptor!");
			return -1;
		}
		if (count < 0){
			Lib.debug(dbgProcess, "Byte count is negative!");
		}

		byte[] buffer = new byte[pageSize];

		int readCnt = 0;
		while(readCnt < count){
			int curCnt = 0;
			if (count - readCnt >= pageSize){
				curCnt = this.readVirtualMemory(vaddr, buffer, 0, pageSize);
				if (curCnt < pageSize){
					Lib.debug(dbgProcess, "Write error(1)!");
					return -1;
				}
				curCnt = fileArray[fd].write(buffer, 0, curCnt);
				if (curCnt < pageSize){
					Lib.debug(dbgProcess, "Write error(2)!");
					return -1;
				}
			}else{
				int leftCnt = count - readCnt;
				curCnt = this.readVirtualMemory(vaddr, buffer, 0, leftCnt);
				if (curCnt < leftCnt){
					Lib.debug(dbgProcess, "Write error(3)!");
					return -1;
				}
				curCnt = fileArray[fd].write(buffer, 0, curCnt);
				if (curCnt < leftCnt){
					Lib.debug(dbgProcess, "Write error(4)!");
					return -1;
				}
			}
			readCnt += curCnt;
			vaddr += curCnt;
		}
		
		if (readCnt < count)
			return -1;
		return readCnt;

	}

	private int handleClose(int fd){
		if (fd < 0 || fd > MAX_FILE || fileArray[fd] == null){
			Lib.debug(dbgProcess, "The file to close is out of range or does not exist!");
			return -1;
		}
		fileArray[fd].close();
		fileArray[fd] = null;
		fileNum--;
		return 0;
	}

	private int handleUnlink(int vaddr){
		String fileName = readVirtualMemoryString(vaddr, 256);
		if (fileName == null){
			Lib.debug(dbgProcess, "The file to remove does not exist!");
			return -1;
		}
		int fd = -1;
		for (int i = 0; i < MAX_FILE; ++i){
			if (fileArray[i] != null && fileArray[i].getName().equals(fileName)){
				fd = i;
				break;
			}
		}

		if (fd != -1){
			handleClose(fd);
		}

		if (ThreadedKernel.fileSystem.remove(fileName))
			return 0;
		return -1;

	}


	private int handleExec(int file, int argcnt, int argval){

//		Lib.debug(dbgProcess, "Exec: Executable has " + argcnt + " argument.");

		if (argcnt < 1){
			Lib.debug(dbgProcess, "Exec Failed: argument is less than 1.");
			return -1;
		}

		String fileName = readVirtualMemoryString(file, pageSize);

		if (fileName == null){
			Lib.debug(dbgProcess, "Exec failed: illegal file name or argument!");
			return -1;
		}

//		Lib.debug(dbgProcess, "Exec: File name " + fileName);

		/*
		String suffix = fileName.substring(fileName.length() - 5, fileName.length());
		if (!suffix.equals(".coff")){
			Lib.debug(dbgProcess, "Exec Failed: Not with suffix .coff! " + suffix);
			return -1;
		}
		*/

		String[] args = new String[argcnt];
		byte[] argAddr = new byte[4];
		for (int i = 0; i < argcnt; ++i){
			int cnt = readVirtualMemory(argval, argAddr);
			if (cnt != 4){
				Lib.debug(dbgProcess, "Exec Failed: reading argval address!");
				return -1;
			}
			argval += 4;
			args[i] = readVirtualMemoryString(Lib.bytesToInt(argAddr, 0), pageSize);
			if (args[i] == null){
				Lib.debug(dbgProcess, "Exec Failed: argument number does not match!");
				return -1;
			}
			Lib.debug(dbgProcess, "Exec: Argument, " + args[i])
		}


		/*
		for (int i = 0; i < argcnt; ++i){
			Lib.debug(dbgProcess, "Exec: Argument " + args[i]);
		}
		*/

		UserProcess child = UserProcess.newUserProcess();
		this.childrenProcess.put(child.pID, child);
		child.setParent(this.pID, this);

//		Lib.debug(dbgProcess, "Exec: Set child process");

		if (child.execute(fileName, args)){
//			Lib.debug(dbgProcess, "Exec: Executed");
			int childPID = child.getPID();
			Lib.debug(dbgProcess, "Exec: Child pid: " + childPID);
			return child.getPID();
		}

//		Lib.debug(dbgProcess, "Exec failed: failed when executing child process!");
		return -1;

	}

	private int handleJoin(int pid, int status){
		if (this.childrenProcess.containsKey(pid) == false){
			Lib.debug(dbgProcess, "Join Failed: Wrong child PID!");
			return -1;
		}
		UserProcess child = this.childrenProcess.get(pid);

		this.lock.acquire();
		while(child.exitStatus == null){
			cond.sleep();
		}
		this.lock.release();

		int childExitStatus = child.exitStatus.intValue();

		if (writeVirtualMemory(status, Lib.bytesFromInt(childExitStatus)) < 0){
			Lib.debug(dbgProcess, "Join Failed: Cannot write to status!");
			return -1;
		}

		if (childExitStatus == 0)
			return 1;
		else
			return 0;

	}

	/*
	protected int handleExit(int status) {
	for (int i=0; i<maxFiles; i++)
	    handleClose(i);

	UserKernel.memoryLock.acquire();

	unloadSections();

	UserKernel.memoryLock.release();

	coff.close();
	
	UserKernel.processLock.acquire();

	if (parentProcess != null) {
	    Integer value = abnormalTermination ? null : new Integer(status);
	    parentProcess.exitStatusTable.put(new Integer(processID), value);
	    parentProcess.childFinished.wake();
	}

	if (--UserKernel.numRunningProcesses == 0)
	    Kernel.kernel.terminate();

	UserKernel.processLock.release();
	KThread.finish();

	Lib.assertNotReached("KThread.finish() did not finish thread!");
	return 0;
    }

    */

    
	private void handleExit(int status){

		Lib.debug(dbgProcess, "Exiting...");
	
		this.exitStatus = status;

		for (int i = 0; i < MAX_FILE; ++i){
			if (fileArray[i] != null)
				handleClose(i);
		}

		for (UserProcess child : childrenProcess.values()){
//			child.lock.acquire();
			child.setParent(UserKernel.rootPID, UserKernel.root);
			child.parentPID = -1;
//			child.lock.release();
		}
		childrenProcess.clear();

		unloadSections();

		coff.close();

		this.lock.acquire();
		if (parentProcess != null){
			parentProcess.lock.acquire();
			parentProcess.cond.wake();
			parentProcess.lock.release();
		}
		this.lock.release();

		int pCount = 0;
		UserKernel.pCountMutex.P();
		pCount = --UserKernel.pCount;
		UserKernel.pCountMutex.V();

		if (this == UserKernel.root){
			Kernel.kernel.terminate();
		}

		UThread.finish();

		Lib.assertNotReached();

	}
	

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallCreate:
			return handleCreate(a0);

		case syscallOpen:
			return handleOpen(a0);

		case syscallRead:
			return handleRead(a0, a1, a2);

		case syscallWrite:
			return handleWrite(a0, a1, a2);

		case syscallClose:
			return handleClose(a0);

		case syscallUnlink:
			return handleUnlink(a0);

		case syscallExec:
			return handleExec(a0, a1, a2);

		case syscallJoin:
			return handleJoin(a0, a1);

		case syscallExit:
			handleExit(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/* The file descriptor array.*/
	private OpenFile[] fileArray = new OpenFile[16];

	private int fileNum;

	private static final int MAX_FILE = 16;
	private static final int BUF_SIZE = 256;

	private Hashtable<Integer,UserProcess> childrenProcess = new Hashtable<Integer, UserProcess>();
	private int parentPID = -1;
	private UserProcess parentProcess;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	//CSE120 Proj2 Part2
	protected int pID;

	private Lock lock = new Lock();
	private Condition cond = new Condition(lock);

	private Integer exitStatus = null;
}
