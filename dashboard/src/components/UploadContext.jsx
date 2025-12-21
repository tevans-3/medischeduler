import React, { useState, useContext } from 'react'; 

export const UploadContext = React.createContext(); 

export function UploadProvider({ children }) { 
    const [studentUploadData, setStudentUploadData] = useState(null); 
    const [teacherUploadData, setTeacherUploadData] = useState(null);
    const [studentUploadStatus, setStudentUploadStatus] = useState('initial'); 
    const [teacherUploadStatus, setTeacherUploadStatus] = useState('initial'); 
    const [studentValStatus, setStudentValStatus] = useState('unknown'); 
    const [teacherValStatus, setTeacherValStatus] = useState('unknown'); 
    const [isCheckedStudentBox, setIsCheckedStudentBox] = useState(false);
    const [isCheckedTeacherBox, setIsCheckedTeacherBox] = useState(false); 
    return (
        <UploadContext.Provider value={{ studentUploadData, 
                                         setStudentUploadData, 
                                         studentUploadStatus,
                                         setStudentUploadStatus,
                                         studentValStatus, 
                                         setStudentValStatus, 
                                         teacherUploadData, 
                                         teacherUploadStatus,
                                         setTeacherUploadStatus,
                                         setTeacherUploadData,
                                         teacherValStatus,
                                         setTeacherValStatus,
                                         isCheckedStudentBox, 
                                         setIsCheckedStudentBox,
                                         isCheckedTeacherBox, 
                                         setIsCheckedTeacherBox
                                         }}>
            {children}
        </UploadContext.Provider>
    ); 
}

export function useUpload() {
  return useContext(UploadContext);
}