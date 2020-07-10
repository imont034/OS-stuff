package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.userprog.UserKernel;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.util.*;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
	    	pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
		this.fileDescriptorTable = new OpenFile[16];
		//In accordance to UNIX standard files, file number 0 is for stdin and file number 1 is for stdout, and 2 is for
		//standard error
		this.fileDescriptorTable[0] = UserKernel.console.openForReading();
		this.fileDescriptorTable[1] = UserKernel.console.openForWriting();
        //this.parent = null;
		this.pid = UserKernel.pid;
		UserKernel.pid++;
		this.children = new Hashtable<Integer, UserProcess>();
		this.argc = 1;
		//this.arguments = new String[this.argc][];
        this.arguments = new String[this.argc];
        mutex = new Lock();
        this.terminatedChildren = new HashMap<Integer, Integer>();
	}
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
		//if (!load(name, args))
	    //	return false;
	
		new UThread(this).setName(name).fork();

		return true;
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
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
	    	if (bytes[length] == 0)
			return new String(bytes, 0, length);
	}

		return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
	
		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
	    	return 0;

		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
	    	return 0;

		int amount = Math.min(length, memory.length-vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
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
		for (int s=0; s<coff.getNumSections(); s++) {
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
		for (int i=0; i<args.length; i++) {
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
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
	    	return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;
	
		for (int i=0; i<argv.length; i++) {
	    	byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    	Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    	entryOffset += 4;
	    	Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    	stringOffset += argv[i].length;
	    	Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    	stringOffset += 1;
		}

		return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
	    	coff.close();
	    	Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    	return false;
		}

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
	    	CoffSection section = coff.getSection(s);
	    
	    	Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      	+ " section (" + section.getLength() + " pages)");

	    	for (int i=0; i<section.getLength(); i++) {
			int vpn = section.getFirstVPN()+i;

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
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
	    	processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    	//halt can only be invoked by the "root" process. If another process tries to invoke halt, return immediately.
    	if(this!=UserKernel.rootProcess) return -1;
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
    }

    /*
    * Handle the close() system call.
    * @param fileDescriptor the specific position within the file descriptor table on where the user wants to close some file
    */
    private int handleClose(int fileDescriptor){
    	//Cannot close indices that are out of range. Anything else can be closed including standard input and
		// standard output.
    	if ((fileDescriptor < 0) || (fileDescriptor > 15)){
    		Lib.debug(dbgProcess,"\thandleClose: Trying to close the specific file in index " +
					fileDescriptor + "does not work.\n Terminating...");
			return -1;
    	}
    	if (fileDescriptorTable[fileDescriptor] == null){
    		Lib.debug(dbgProcess, "handleClose: Trying to close a null file at index" + fileDescriptor + "Terminating...\n");
    		return -1;
		}
    	fileDescriptorTable[fileDescriptor].close();
    	fileDescriptorTable[fileDescriptor] = null;
    	return 0;
	}

	/*
	* Handle the create() system call.
	* @param pathName is the specified file.
	* @return return the index within the file descriptor table on where the file is created if successful or -1 if fail.
	*/
	private int handleCreate(String pathName){
        if(pathName.isEmpty() || pathName.isBlank() || pathName.equals(" ")) {
            Lib.debug(dbgProcess, "handleUnlink: Pathname is empty or blank. Terminating...\n");
            return -1;
        }
		OpenFile fileToBeCreated = ThreadedKernel.fileSystem.open(pathName, true);
		if(fileToBeCreated == null){
			Lib.debug(dbgProcess,"handleCreate: could not open file. Terminating...\n");
			return -1;
		}

		int i = 0;
		//Begin at two for we do not wish to create any files within stdin and stdout
		for(i = 2; i < this.fileDescriptorTable.length; i++){
			//Wish to look for the first empty position within the table
			if(this.fileDescriptorTable[i] == null){
				this.fileDescriptorTable[i] = fileToBeCreated;
				return i;
			}
		}
		return -1;
	}



	/*
	* Handle the open() system call.
	* Essentially, handleOpen is a wrapper for int open(const char *pathName, int flags)
	* However, since we are not utilizing any flags,we are simply opening a file specified by
	* @param pathName is the specified file.
	* @return index of where the file is located at within the file descriptor if successful or -1 otherwise.
	*/
	private int handleOpen(String pathName){
        if(pathName.isEmpty() || pathName.isBlank() || pathName.equals(" ")) {
            Lib.debug(dbgProcess, "handleUnlink: Pathname is empty or blank. Terminating...\n");
            return -1;
        }
        OpenFile fileToBeOpened = ThreadedKernel.fileSystem.open(pathName, false);
        if(fileToBeOpened == null) {
            Lib.debug(dbgProcess, "\tFile called " + pathName + "cannot be opened for it does not exist");
            return -1;
        }

        int i = 0;
        for (i = 2; i < this.fileDescriptorTable.length; i++){
            if(this.fileDescriptorTable[i] == null){
                this.fileDescriptorTable[i] = fileToBeOpened;
                return i;
            }
        }
        return -1;
    }

	/*
	* handle the read() system call.
	* @param fileDescriptor file associated with the open file descriptor
	* @param buff the buffer pointer within the file
	* @param count bytes from the file associated with the open file descriptor
	*/
	private int handleRead(int fileDescriptor, int buff, int count){
		if(count < 0){
		    Lib.debug(dbgProcess, "Cannot read bytes less than 0 within a file. Terminating...\n");
		    return -1;
        }
		if(count == 0){
		  Lib.debug(dbgProcess, "Cannot read with 0 bytes. Terminating...\n");
		  return -1;
        }
		//Can read from stdin and stdout so only need to worry about files that are out of range within the file descriptor
	    if(fileDescriptor  < 0 ||  fileDescriptor > 15) {
			Lib.debug(dbgProcess, "\t handleRead: trying to read a file from virtual address out of range," +
                    "trying to read from address "+ fileDescriptor + '\n');
			return -1;
		}
		if(this.fileDescriptorTable[fileDescriptor] == null){
			Lib.debug(dbgProcess, "\t handleRead: file to be read does not exist within the file descriptor");
			return -1;
		}

		OpenFile fileToBeRead = this.fileDescriptorTable[fileDescriptor];
		byte[] buffer = new byte[count];
		int bytesToRead, bytesRead;
		bytesToRead = readVirtualMemory(buff, buffer, 0, count);
		bytesRead = fileToBeRead.read(buffer, 0, bytesToRead);
		if(bytesRead == -1){
			Lib.debug(dbgProcess, "handleRead: Failed to read from file. Terminating...\n");
			return -1;
		}
		int bytesWritten = fileToBeRead.write(buff, buffer, 0, bytesRead);
		if(bytesWritten == -1){
			Lib.debug(dbgProcess, "handleRead: Cannot decipher the number of bytes that were written previously. Terminating...\n");
			return -1;
		}
		if(bytesWritten != bytesRead){
			Lib.debug(dbgProcess, "handleReaD: write and read amounts not the same. Improper read. Terminating...\n");
			return -1;
		}
		return writeVirtualMemory(buff,buffer,0,bytesRead);
	}

	/*
	* Handles the write() system call. Write to a file descriptor
	* @param fileDescriptor file referred to by the file descriptor fileDescriptor
	* @param buff buffer starting at pointer buff
	* @param count amount of bytes written from buffer starting at buff
	* @return number of bytes written on success. Otherwise, -1.
	*/
	private int handleWrite(int fileDescriptor, int buff, int count){
        if(fileDescriptor == 0){
            Lib.debug(dbgProcess, "\thandleWrite: Trying to write into stdin. Terminating...\n");
            return -1;
        }
	    if(fileDescriptor < 1 || fileDescriptor > 15){
            Lib.debug(dbgProcess, "handleWrite: Trying to access a file to write that is not within " +
                    "the bounds of the file descriptor table. Terminating...\n");
            return -1;
        }
        if(count < 0){
            Lib.debug(dbgProcess, "handleWrite: Cannot write to a file with negative bytes. Terminating...\n");
            return -1;
        }
        if(count == 0){
            Lib.debug(dbgProcess, "handleWrite: Cannot write to a file with 0 bytes. Terminating...\n");
            return -1;
        }

        int bytesToBeRead;
        int totalBytesWritten;

        if(this.fileDescriptorTable[fileDescriptor] == null){
            Lib.debug(dbgProcess, "handleWrite: File to be written is null. Terminating...\n");
            return -1;
        }

		OpenFile fileToBeWritten = this.fileDescriptorTable[fileDescriptor];

        //Byte array of the amount of bytes to write
        byte[] buffer = new byte[count];

        //Write from virtual address space to buffer
        bytesToBeRead = readVirtualMemory(buff, buffer, 0, count);
        if(bytesToBeRead <=0){
            Lib.debug(dbgProcess, "handleWrite: Failed to read from virtual memory\n");
            return -1;
        }
        totalBytesWritten = fileToBeWritten.write(buffer, 0, bytesToBeRead);
        if(totalBytesWritten == -1){
            Lib.debug(dbgProcess, "\thandleWrite: failed to write from file. Terminating...\n");
            return -1;
        }
	    //POSIX requires that a read() that can be proved to occur after a write()
        //has returned the new data. If the bytes written and read are not the same, then we know it has not
		//been written properly.
        int readBytes = fileToBeWritten.read(buffer, 0, bytesToBeRead);
        if(readBytes == -1){
            Lib.debug(dbgProcess, "handleWrite: Failed to read from file. Terminating...\n");
            return -1;
        }
        if(totalBytesWritten!= readBytes){
            Lib.debug(dbgProcess, "handleWrite: Did not write properly. Terminating...\n");
            return -1;
        }

        return totalBytesWritten;
	}

	/*
	* handle the unlink() system call. It deletes a name from the file system. If that name was the last link to
	* a file and no processes have the file open, the file is deleted and the space it was using is made available for
	* reuse.
	* @param pathName is the name of the file
	* @return 0 if successful, -1 otherwise.
	*/
	private int handleUnlink(String pathName){
		//If the pathName is null or empty, return -1;
		if(pathName.isEmpty() || pathName.isBlank() || pathName.equals(" ")) {
			Lib.debug(dbgProcess, "handleUnlink: Pathname is empty or blank. Terminating...\n");
			return -1;
		}

		//Iterating through the file descriptor table and check what the name of the file is and compare it with the
		//pathname that we want to unlink.If it does exit, we will then get the index within the file descriptor table
		// where we can then unlink it.
		int index = 0;
		for(int i = 0; i < fileDescriptorTable.length; i++){
		    OpenFile file = fileDescriptorTable[i];
		    if(file != null && pathName.equals(file.getName())) index = i;
        }
		if(index == 0){
		    Lib.debug(dbgProcess, "handleUnlink: pathName does not exist within the file descriptor. Terminating...\n");
		    return -1;
        }

        fileDescriptorTable[index].close();
        fileDescriptorTable[index] = null;
        if(ThreadedKernel.fileSystem.remove(pathName)) return 0;
        Lib.debug(dbgProcess, "handleUnlink: Cannot remove the file properly within the file descriptor. Terminating...\n");
        return -1;

	}

	/*
	*  Handle the exec() system call.
	* @param vaddr the beginning position of where we would be reading virtual memory
	* @param argc amount of arguments when executing
	* @return pid of new process being executed or -1 if failure.
	*/
	private int handleExec(String pathName, int argc, int argv){
		if(argc < 0){
			Lib.debug(dbgProcess, "handleExec: negative arguments. Incorrect number of arguments");
			return -1;
		}
        if (argv < 0){
            Lib.debug(dbgProcess, "handleExec: negative argv.\n");
            return -1;
        }
        if(pathName.isEmpty() || pathName.isBlank() || pathName.equals(" ")) {
            Lib.debug(dbgProcess, "handleExec: Pathname is empty or blank. Terminating...\n");
            return -1;
        }

		OpenFile fileExecutedPreserved = ThreadedKernel.fileSystem.open(pathName, false);
		if(fileExecutedPreserved == null){
		    Lib.debug(dbgProcess, "handleExec: Could not preserve file executed.");
		    return -1;
        }

        //store file in descriptor to be preserved, which will be tracked by the running process object
		int i = 0;
		for( i =2; i < this.fileDescriptorTable.length; i++){
		    if(this.fileDescriptorTable[i] == null){
		        this.fileDescriptorTable[i] = fileExecutedPreserved;
		        break;
            }
        }
		if(i == this.fileDescriptorTable.length){
		    Lib.debug(dbgProcess, "The file descriptor does not have any more space in order to preserve executed file.");
		    return -1;
        }

		this.argc = argc;
		this.argv = argv;
		//this.arguments = new String[this.argc][];
		//byte[] buffer = new byte[4];

		//byte[][] argv = new byte[arguments.length][];
        //looks like the arguments will be a single string array
        this.arguments = new String[this.argc];

        //buffer to read virtual memory into
        byte[] buffer = new byte[4 * argc];

		for(i=0; i < this.argc; i++){
			this.arguments[i] = readVirtualMemoryString(Lib.bytesToInt(buffer,0), MAXSTRINGLENGTH);
			if(arguments[i] == null){
				Lib.debug(dbgProcess, "handleExec: Error reading from virtual memory");
				return -1;
			}
		}

        //Instantiating the new process that will be executed
		UserProcess executable = newUserProcess();

        //Putting the child process within a hash table that allows for the parent process
        //to find either of their child processes through their pid.
		this.children.put(executable.pid, executable);

		//Having the process that has just been executed to be the child of this. Thus,
        // in consequence, having this be the parent.
		executable.parent = this;

		//A check to see if we can load the executable with the specified name into the process,
        // and then prepare its arguments to see if they can all fit within a page.
		if(!executable.load(pathName, this.arguments)) {
		    Lib.debug(dbgProcess, " handleExec: " +
                    "Cannot load in the executable with the specified name" + pathName +
                    "into the current process" + this.pid);
		    return -1;
        }

		//If we reach here, then we can execute the specified program with the specified
        //arguments.
		if(executable.execute(pathName, arguments)) return executable.pid;

		Lib.debug(dbgProcess, "handleExec: Cannot execute the child process properly.Terminating...\n");
		return -1;
	}
	/*
    * handles the exit() system call. exit() terminates the calling process immediately. Any open
    * file descriptors belonging to to the processes are closed. Any children of the process are inherited by
    * process 1, init, and the process's parent is sent a SIGCHLD signal.
    * @param status
    * @return 0 if successful, -1 otherwise.
    */
	private int handleExit(int status){
	    //we begin by closing all files that are associated with this process.
        for(int i = 0; i < this.fileDescriptorTable.length; i++){
            if(this.fileDescriptorTable[i]!=null){
                this.fileDescriptorTable[i].close();
                this.fileDescriptorTable[i]=null;
            }
        }
        //begin to take away all of the child processes and changing them to init process
        this.children.forEach((k,v) -> {
            //v.parent = null;
            v.parent = UserKernel.rootProcess;
            this.children.replace(k,v);
        });
        this.unloadSections();
        if(this.pid == 0) Kernel.kernel.terminate();
        else UThread.finish();
	    return status;
    }

    /*
    *  @param processID is the process ID of the child process, returned by exec().
    *  @param  status points to an integer where the exit status of the child process will
    *  be stored. This is the value the child passed to exit(). If the child exited
    *  because of an unhandled exception, the value stored is not defined.
    */
    private int handleJoin(int processID, int status){
        if(processID <0 || status < 0) {
            Lib.debug(dbgProcess, "The processID or status are out of range.\n");
            return -1;
        }
        mutex.acquire();
        UserProcess child = null;
        for(int i = 0; i < this.children.size(); i++){
            if(this.children.get(i).pid == this.pid){
                child = this.children.get(i);
                break;
            }
        }
        if(child == null){
            Lib.debug(dbgProcess, "handleJoin: this.pid is not the same as of the child");
            mutex.release();
            return -1;
        }
        //check for status now that's my TODO:
        child.thread.join();
        this.children.remove(child.pid);
        child.parent=null;
        mutex.release();
	    return -1;
    }

    private static final int
        syscallHalt = 0,
		syscallExit = 1,
		syscallExec = 2,
		syscallJoin = 3,
		syscallCreate = 4,
		syscallOpen = 5,
		syscallRead = 6,
		syscallWrite = 7,
		syscallClose = 8,
		syscallUnlink = 9;

     /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallClose:
				return handleClose(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0,a1,a2);
            case syscallExit:
                return handleExit(a0);
            case syscallExec:
                {
                    String pathName = readVirtualMemoryString(a0, MAXSTRINGLENGTH);
                    if(pathName == null){
                        Lib.debug(dbgProcess, "syscallExec: Cannot read from virtual memory\n");
                        return -1;
                    }
                    return handleExec(pathName, a1, a2);
                }
            case syscallCreate:
                {
                    String pathName = readVirtualMemoryString(a0, MAXSTRINGLENGTH);
                    if(pathName == null){
                        Lib.debug(dbgProcess, "syscallCreate: Cannot read from virtual memory\n");
                        return -1;
                    }
                    return handleCreate(pathName);
                }
            case syscallOpen:
                {
                    String pathName = readVirtualMemoryString(a0, MAXSTRINGLENGTH);
                    if(pathName == null){
                        Lib.debug(dbgProcess, "syscallOpen: cannot read from virtual Memory\n");
                        return -1;
                    }
                    return handleOpen(pathName);
                }
            case syscallUnlink:
                {
                    if (a0 < 0) {
                        Lib.debug(dbgProcess, "syscallUnlink: Invalid argument for unlink(). Terminating...\n");
                        return -1;
                    }
                    String pathName = readVirtualMemoryString(a0, MAXSTRINGLENGTH);
                    if (pathName == null) {
                        Lib.debug(dbgProcess, "syscallUnlink: Cannot read from virtual memory. Terminating..\n");
                        return -1;
                    }
                    return handleUnlink(pathName);
                }
			default:
	    		Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    		Lib.assertNotReached("Unknown system call!");
			}
		return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
	    	int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    	processor.writeRegister(Processor.regV0, result);
	    	processor.advancePC();
	    	break;
				       
		default:
	    	Lib.debug(dbgProcess, "Unexpected exception: " +
		      	Processor.exceptionNames[cause]);
	    	Lib.assertNotReached("Unexpected exception");
		}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;

    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    private final OpenFile[] fileDescriptorTable;
    private final int MAXSTRINGLENGTH=256;
    private UserProcess parent;
    private Hashtable<Integer, UserProcess> children;
    private int initialPC, initialSP;
    private int argc, pid, argv;
    private String[] arguments;
    private Lock mutex;
    private HashMap<Integer, Integer> terminatedChildren;
    private UThread thread;
    //private String[][] arguments;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
}
