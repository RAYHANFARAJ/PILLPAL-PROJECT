import React, { useState, useEffect, useRef, Component } from 'react';
import { View, Text, TextInput, TouchableOpacity, SectionList, Alert, StyleSheet, Image, Linking, SafeAreaView, Platform, KeyboardAvoidingView } from 'react-native';
import { Camera, useCameraDevices } from 'react-native-vision-camera';
import { Picker } from '@react-native-picker/picker';
import Icon from 'react-native-vector-icons/MaterialIcons';
import Header from '../../components/Header';
import PrescriptionCard from '../../components/PrescriptionCard';
import { getPrescriptionsByPatient, createPrescription, updatePrescription, deletePrescription, getPatients, scanPrescription } from '../../api/doctor';
import { logout } from '../../utils/auth';


class ErrorBoundary extends Component {
  state = { hasError: false, error: null };

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      return (
        <View style={styles.errorContainer}>
          <Text style={styles.redText}>Erreur: {this.state.error?.message || 'Une erreur est survenue'}</Text>
          <TouchableOpacity style={styles.blueButton} onPress={() => this.setState({ hasError: false, error: null })}>
            <Text style={styles.buttonText}>Réessayer</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return this.props.children;
  }
}

const PrescriptionScreen = ({ navigation }) => {
  const [patientId, setPatientId] = useState('');
  const [patients, setPatients] = useState([]);
  const [prescriptions, setPrescriptions] = useState([]);
  const [form, setForm] = useState({
    patientId: '',
    medicationName: '',
    dosage: '',
    startDate: '',
    endDate: '',
    doseTimes: [{ hour: '', minute: '' }],
  });
  const [mode, setMode] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [hasPermission, setHasPermission] = useState(null);
  const [photo, setPhoto] = useState(null);
  const devices = useCameraDevices();
  const device = devices.back
    ? Array.isArray(devices.back)
      ? devices.back[0]
      : devices.back
    : Object.values(devices).find(d => d && d.position === 'back') || null;
  const cameraRef = useRef(null);

  useEffect(() => {
    console.log('TEST LOG: PrescriptionScreen mounted at ' + new Date().toISOString());
    const checkCameraPermission = async () => {
      try {
        const status = await Camera.requestCameraPermission();
        console.log('Camera permission status:', status);
        setHasPermission(status === 'granted');
        if (status !== 'granted') {
          Alert.alert(
            'Permission refusée',
            'L’accès à la caméra est requis pour scanner les prescriptions.',
            [
              { text: 'Annuler', style: 'cancel' },
              { text: 'Ouvrir paramètres', onPress: () => Linking.openSettings() },
            ]
          );
        }
      } catch (error) {
        console.error('Error checking camera permission:', error);
        Alert.alert('Erreur', 'Échec de la vérification de la permission caméra: ' + error.message);
        setHasPermission(false);
      }
    };

    console.log('Available devices:', JSON.stringify(devices, null, 2));
    console.log('Selected device:', device ? JSON.stringify(device, null, 2) : 'Not available');
    checkCameraPermission();
    getPatients()
      .then((res) => {
        console.log('Patients:', res.data);
        setPatients(Array.isArray(res.data) ? res.data : []);
      })
      .catch((error) => {
        console.error('Error fetching patients:', error.response?.data || error.message);
        Alert.alert('Erreur', 'Impossible de charger les patients');
        setPatients([]);
      });
  }, [devices]);

  useEffect(() => {
    console.log('Current hasPermission state:', hasPermission);
    console.log('Current device state:', device ? 'Available' : 'Not available');
    if (patientId) {
      console.log('Fetching prescriptions for patientId:', patientId);
      fetchPrescriptions();
      setForm((prevForm) => ({ ...prevForm, patientId }));
    } else {
      setPrescriptions([]);
      setForm((prevForm) => ({ ...prevForm, patientId: '' }));
    }
  }, [patientId, hasPermission, device]);

  const fetchPrescriptions = async () => {
    if (!patientId) {
      console.log('No patientId, resetting prescriptions');
      setPrescriptions([]);
      return;
    }
    try {
      console.log('Calling getPrescriptionsByPatient with patientId:', patientId);
      const res = await getPrescriptionsByPatient(patientId);
      console.log('Raw API prescriptions response:', JSON.stringify(res, null, 2));
      if (!res.data) {
        console.warn('No data in res.data');
        setPrescriptions([]);
        Alert.alert('Info', 'Aucune prescription trouvée pour ce patient');
        return;
      }
      if (!Array.isArray(res.data)) {
        console.error('Error: res.data is not an array:', res.data);
        setPrescriptions([]);
        Alert.alert('Erreur', 'Réponse invalide du serveur');
        return;
      }
      const validPrescriptions = res.data.filter(
        (p) => p && p.id != null && typeof p.id !== 'undefined'
      );
      console.log('Filtered prescriptions:', validPrescriptions);
      setPrescriptions(validPrescriptions);
    } catch (error) {
      console.error('Error fetching prescriptions:', error.response?.data || error.message);
      Alert.alert('Erreur', 'Impossible de charger les prescriptions: ' + (error.response?.data?.message || error.message));
      setPrescriptions([]);
    }
  };

  const handleScan = async () => {
    console.log('handleScan called', {
      hasPermission,
      device: !!device,
      cameraRef: !!cameraRef.current,
    });
    if (!device) {
      console.error('No back camera device available:', devices);
      Alert.alert('Erreur', 'Aucune caméra détectée. Vérifiez si une caméra est disponible sur cet appareil.');
      return;
    }
    if (hasPermission === null) {
      console.warn('Camera permission not yet checked');
      Alert.alert('Erreur', 'La permission caméra n\'a pas encore été vérifiée. Veuillez réessayer.');
      return;
    }
    if (!hasPermission) {
      console.warn('Camera permission not granted');
      Alert.alert('Erreur', 'Permission caméra non accordée. Veuillez activer la permission dans les paramètres.');
      return;
    }
    if (!cameraRef.current) {
      console.error('Camera ref not initialized');
      Alert.alert('Erreur', 'Caméra non initialisée. Veuillez réessayer.');
      return;
    }
    try {
      console.log('Attempting to take photo with cameraRef:', !!cameraRef.current);
      const takenPhoto = await cameraRef.current.takePhoto({
        quality: 1.0,
        skipMetadata: true,
      });
      console.log('Photo taken:', JSON.stringify(takenPhoto, null, 2));
      const photoUri = Platform.OS === 'android' ? `file://${takenPhoto.path}` : takenPhoto.path;
      setPhoto(photoUri);
    } catch (error) {
      console.error('Error in handleScan:', error);
      Alert.alert('Erreur', 'Échec de la capture de l’image: ' + error.message);
      setMode('manual');
      setPhoto(null);
    }
  };

  const handleUploadScan = async () => {
  console.log('handleUploadScan appelé', { photo, patientId });
  if (!photo) {
    console.warn('Aucune photo sélectionnée');
    Alert.alert('Erreur', 'Aucune photo sélectionnée');
    return;
  }
  if (!patientId) {
    console.warn('Aucun patientId sélectionné');
    Alert.alert('Erreur', 'Veuillez sélectionner un patient');
    return;
  }
  try {
    const file = { uri: photo, type: 'image/jpeg', name: 'scan.jpg' };
    console.log('Envoi du fichier:', file);
    const res = await scanPrescription(patientId, file);
    console.log('Réponse brute de scanPrescription:', JSON.stringify(res, null, 2));
    console.log('Données de la réponse:', JSON.stringify(res.data, null, 2));

    // Vérifier si la réponse est vide
    if (!res.data || Object.keys(res.data).length === 0) {
      console.warn('Réponse OCR vide:', res.data);
      Alert.alert('Erreur', 'Aucune donnée extraite de l’image. Vérifiez la qualité de l’image et réessayez.');
      setMode('manual');
      setPhoto(null);
      return;
    }

    const {
      medicationName,
      dosage,
      startDate,
      endDate,
      doseTimes,
      Medication,
      Dosage,
      Start,
      End,
      Times,
      médicament,
      posologie,
      début,
      fin,
      horaires,
    } = res.data;

    console.log('Champs extraits:', {
      medicationName: medicationName || Medication || médicament,
      dosage: dosage || Dosage || posologie,
      startDate: startDate || Start || début,
      endDate: endDate || End || fin,
      doseTimes: doseTimes || Times || horaires,
    });

    // Vérifier si tous les champs sont vides
    const extractedFields = {
      medicationName: medicationName || Medication || médicament || '',
      dosage: dosage || Dosage || posologie || '',
      startDate: startDate || Start || début || '',
      endDate: endDate || End || fin || '',
      doseTimes: doseTimes || Times || horaires || '',
    };
    const allFieldsEmpty = Object.values(extractedFields)
      .filter(val => val !== extractedFields.patientId)
      .every(val => !val);
    if (allFieldsEmpty) {
      console.warn('Tous les champs extraits sont vides:', res.data);
      Alert.alert('Erreur', 'Aucune donnée valide extraite. Vérifiez l’image (qualité, texte) ou entrez manuellement.');
      setMode('manual');
      setPhoto(null);
      return;
    }

    // Parser les doseTimes
    let parsedDoseTimes = [{ hour: '', minute: '' }];
    if (extractedFields.doseTimes) {
      try {
        const timeArray = extractedFields.doseTimes.split(',').map(t => t.trim()).filter(t => t);
        parsedDoseTimes = timeArray.map(time => {
          if (!time.match(/^\d{2}:\d{2}$/)) {
            throw new Error(`Format d'heure invalide: ${time}`);
          }
          const [hour, minute] = time.split(':').map(Number);
          if (isNaN(hour) || isNaN(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new Error(`Valeurs d'heure invalides: ${time}`);
          }
          return { hour: hour.toString(), minute: minute.toString() };
        });
        console.log('Horaires parsés:', parsedDoseTimes);
      } catch (error) {
        console.error('Erreur lors du parsing des doseTimes:', error.message);
        Alert.alert('Avertissement', `Format des horaires invalide: ${error.message}. Veuillez entrer manuellement.`);
      }
    } else {
      console.warn('Aucun horaire trouvé dans la réponse OCR:', extractedFields.doseTimes);
      Alert.alert('Avertissement', 'Aucun horaire trouvé dans l’image scannée. Veuillez entrer manuellement.');
    }

    const newForm = {
      patientId,
      medicationName: extractedFields.medicationName,
      dosage: extractedFields.dosage,
      startDate: extractedFields.startDate,
      endDate: extractedFields.endDate,
      doseTimes: parsedDoseTimes,
    };
    console.log('Mise à jour du formulaire avec:', JSON.stringify(newForm, null, 2));
    setForm(newForm);
    setMode('manual');
    setPhoto(null);
    Alert.alert('Succès', 'Prescription scannée. Vérifiez les champs et complétez manuellement si nécessaire.');
    fetchPrescriptions();
  } catch (error) {
    console.error('Erreur dans handleUploadScan:', error.response?.data || error.message);
    Alert.alert('Erreur', 'Échec du traitement de l’image: ' + (error.response?.data?.message || error.message));
    setMode('manual');
    setPhoto(null);
  }
};

  const handleSubmit = async () => {
    if (!form.patientId || !form.medicationName || !form.dosage || !form.startDate || !form.endDate || form.doseTimes.some(dt => !dt.hour || !dt.minute)) {
      console.warn('Form validation failed', form);
      Alert.alert('Erreur', 'Veuillez remplir tous les champs');
      return;
    }
    try {
      const data = {
        patientId: parseInt(form.patientId),
        medicationName: form.medicationName,
        dosage: form.dosage,
        startDate: form.startDate,
        endDate: form.endDate,
        doseTimes: form.doseTimes.map(dt => ({
          hour: isNaN(parseInt(dt.hour)) ? 0 : parseInt(dt.hour),
          minute: isNaN(parseInt(dt.minute)) ? 0 : parseInt(dt.minute),
        })),
      };
      console.log('Submitting prescription:', data);
      if (editingId) {
        await updatePrescription(editingId, data);
        Alert.alert('Succès', 'Prescription modifiée');
      } else {
        await createPrescription(data);
        Alert.alert('Succès', 'Prescription ajoutée');
      }
      setMode(null);
      setForm({
        patientId: patientId,
        medicationName: '',
        dosage: '',
        startDate: '',
        endDate: '',
        doseTimes: [{ hour: '', minute: '' }],
      });
      setEditingId(null);
      fetchPrescriptions();
    } catch (error) {
      console.error('Error in handleSubmit:', error.response?.data || error.message);
      let errorMessage = 'Échec de l’opération';
      if (error.response?.status === 400) {
        errorMessage = 'Données invalides';
      } else if (error.response?.status === 403) {
        errorMessage = 'Patient non associé';
      } else if (error.response?.status === 404) {
        errorMessage = 'Patient non trouvé';
      } else if (error.response?.status === 500) {
        errorMessage = 'Erreur serveur: ' + (error.response?.data?.message || error.message);
      }
      Alert.alert('Erreur', errorMessage);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deletePrescription(id);
      Alert.alert('Succès', 'Prescription supprimée');
      fetchPrescriptions();
    } catch (error) {
      console.error('Error in handleDelete:', error.response?.data || error.message);
      let errorMessage = 'Échec de la suppression';
      if (error.response?.status === 500 && error.response?.data?.message?.includes('intake_events')) {
        errorMessage = 'Impossible de supprimer: des événements associés existent';
      }
      Alert.alert('Erreur', errorMessage);
    }
  };

  const addDoseTime = () => {
    setForm({ ...form, doseTimes: [...form.doseTimes, { hour: '', minute: '' }] });
  };

  const updateDoseTime = (index, field, value) => {
    console.log(`Updating doseTime[${index}].${field} = ${value}`);
    const newDoseTimes = [...form.doseTimes];
    newDoseTimes[index] = { ...newDoseTimes[index], [field]: value };
    setForm({ ...form, doseTimes: newDoseTimes });
  };

  const handleScanMode = () => {
    console.log('Attempting to enter scan mode', {
      hasPermission,
      device: !!device,
      patientId,
    });
    if (!patientId) {
      console.warn('No patientId selected for scan mode');
      Alert.alert('Erreur', 'Veuillez sélectionner un patient avant de scanner');
      return;
    }
    if (hasPermission === null) {
      console.warn('Camera permission not yet checked');
      Alert.alert('Erreur', 'La permission caméra n\'a pas encore été vérifiée. Veuillez réessayer.');
      return;
    }
    if (!hasPermission) {
      console.warn('Camera permission not granted');
      Alert.alert('Erreur', 'Permission caméra non accordée. Veuillez activer la permission dans les paramètres.');
      return;
    }
    if (!device) {
      console.warn('No camera device available');
      Alert.alert('Erreur', 'Aucune caméra détectée. Vérifiez si une caméra est disponible sur cet appareil.');
      return;
    }
    setMode('scan');
  };

  
  const sections = [
    {
      title: 'Patient Picker',
      data: [{}],
      renderItem: () => (
        <View style={styles.pickerContainer}>
          <Icon name="person" size={20} color="#1E40AF" style={styles.pickerIcon} />
          <Picker
            selectedValue={patientId}
            onValueChange={(value) => {
              console.log('Patient selected:', value);
              setPatientId(value);
            }}
            style={styles.picker}
          >
            <Picker.Item label="Sélectionner un patient" value="" />
            {patients.map((p) => (
              <Picker.Item
                key={p.id || `patient-${Math.random()}`}
                label={`${p.fullName || 'Nom inconnu'} (${p.email || 'Email inconnu'})`}
                value={p.id ? p.id.toString() : ''}
              />
            ))}
          </Picker>
        </View>
      ),
    },
    ...(patientId && !mode
      ? [
          {
            title: 'Prescriptions',
            data: prescriptions.length > 0 ? prescriptions : [{}],
            renderItem: ({ item }) =>
              prescriptions.length > 0 ? (
                <PrescriptionCard
                  prescription={item}
                  onEdit={(p) => {
                    console.log('Editing prescription:', p);
                    setEditingId(p.id);
                    setForm({
                      patientId: patientId || p.patientId?.toString() || '',
                      medicationName: p.medicationName || '',
                      dosage: p.dosage || '',
                      startDate: p.startDate || '',
                      endDate: p.endDate || '',
                      doseTimes: p.doseTimes && Array.isArray(p.doseTimes)
                        ? p.doseTimes.map((time) => ({
                            hour: time.hour?.toString() || '',
                            minute: time.minute?.toString() || '',
                          }))
                        : [{ hour: '', minute: '' }],
                    });
                    setMode('manual');
                  }}
                  onDelete={handleDelete}
                />
              ) : (
                <Text style={styles.noPrescriptionsText}>Aucune prescription pour ce patient.</Text>
              ),
          },
        ]
      : []),
    ...(patientId && !mode
      ? [
          {
            title: 'Actions',
            data: [{}],
            renderItem: () => (
              <View style={styles.buttonRow}>
                <TouchableOpacity style={styles.blueButton} onPress={() => {
                  console.log('Manual mode activated');
                  setMode('manual');
                }}>
                  <Text style={styles.buttonText}>Ajouter manuellement</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.blueButton} onPress={handleScanMode}>
                  <Text style={styles.buttonText}>Scanner</Text>
                </TouchableOpacity>
              </View>
            ),
          },
        ]
      : []),
    ...(mode === 'manual'
      ? [
          {
            title: 'Form',
            data: [{}],
            renderItem: () => (
              <View style={styles.formContainer}>
                <Text style={styles.sectionTitle}>Ajouter/Modifier une prescription</Text>
                <View style={styles.inputContainer}>
                  <Icon name="medication" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Nom du médicament"
                    placeholderTextColor="#333333"
                    value={form.medicationName}
                    onChangeText={(text) => setForm({ ...form, medicationName: text })}
                  />
                </View>
                <View style={styles.inputContainer}>
                  <Icon name="science" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Dosage"
                    placeholderTextColor="#333333"
                    value={form.dosage}
                    onChangeText={(text) => setForm({ ...form, dosage: text })}
                  />
                </View>
                <View style={styles.inputContainer}>
                  <Icon name="calendar-today" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Date début (YYYY-MM-DD)"
                    placeholderTextColor="#333333"
                    value={form.startDate}
                    onChangeText={(text) => setForm({ ...form, startDate: text })}
                  />
                </View>
                <View style={styles.inputContainer}>
                  <Icon name="calendar-today" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Date fin (YYYY-MM-DD)"
                    placeholderTextColor="#333333"
                    value={form.endDate}
                    onChangeText={(text) => setForm({ ...form, endDate: text })}
                  />
                </View>
                {form.doseTimes.map((dose, index) => (
              <View key={index} style={styles.doseTime}>
                <View style={styles.inputContainer}>
                  <Icon name="access-time" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Heure (0-23)"
                    placeholderTextColor="#666666"
                    value={dose.hour}
                    onChangeText={(text) => updateDoseTime(index, 'hour', text)}
                    keyboardType="numeric"
                  />
                </View>
                <View style={styles.inputContainer}>
                  <Icon name="access-time" size={20} color="#1E40AF" style={styles.inputIcon} />
                  <TextInput
                    style={styles.input}
                    placeholder="Minute (0-59)"
                    placeholderTextColor="#666666"
                    value={dose.minute}
                    onChangeText={(text) => updateDoseTime(index, 'minute', text)}
                    keyboardType="numeric"
                  />
                </View>
                {index > 0 && (
                  <TouchableOpacity
                    style={styles.redButton}
                    onPress={() => {
                      const newDoseTimes = form.doseTimes.filter((_, i) => i !== index);
                      setForm({ ...form, doseTimes: newDoseTimes });
                    }}
                  >
                    <Text style={styles.buttonText}>-</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))}
                <TouchableOpacity style={styles.greenButton} onPress={addDoseTime}>
                  <Text style={styles.buttonText}>Ajouter horaire</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.greenButton} onPress={handleSubmit}>
                  <Text style={styles.buttonText}>{editingId ? 'Modifier' : 'Ajouter'}</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.redButton} onPress={() => {
                  console.log('Cancel form, mode:', null);
                  setMode(null);
                  setForm({
                    patientId: patientId,
                    medicationName: '',
                    dosage: '',
                    startDate: '',
                    endDate: '',
                    doseTimes: [{ hour: '', minute: '' }],
                  });
                  setEditingId(null);
                }}>
                  <Text style={styles.buttonText}>Annuler</Text>
                </TouchableOpacity>
              </View>
            ),
          },
        ]
      : []),
    ...(mode === 'scan'
      ? [
          {
            title: 'Scan',
            data: [{}],
            renderItem: () => (
              <View style={[styles.formContainer, styles.scanContainer]}>
                <Text style={styles.sectionTitle}>Scanner une prescription</Text>
                {device && hasPermission ? (
                  <>
                    <Camera
                      ref={(ref) => {
                        cameraRef.current = ref;
                        console.log('Camera ref set:', !!ref);
                      }}
                      style={styles.camera}
                      device={device}
                      isActive={mode === 'scan'}
                      photo={true}
                      enableZoomGesture={false}
                    />
                    <TouchableOpacity style={styles.blueButton} onPress={handleScan}>
                      <Text style={styles.buttonText}>Capturer</Text>
                    </TouchableOpacity>
                    {photo && (
                      <>
                        <Image source={{ uri: photo }} style={styles.preview} />
                        <TouchableOpacity style={styles.greenButton} onPress={handleUploadScan}>
                          <Text style={styles.buttonText}>Traiter l’image</Text>
                        </TouchableOpacity>
                      </>
                    )}
                    <TouchableOpacity
                      style={styles.redButton}
                      onPress={() => {
                        console.log('Cancel scan, mode:', null);
                        setMode(null);
                        setPhoto(null);
                      }}
                    >
                      <Text style={styles.buttonText}>Annuler</Text>
                    </TouchableOpacity>
                  </>
                ) : (
                  <Text style={styles.redText}>
                    Caméra non disponible ou permission refusée. Vérifiez les paramètres de votre appareil.
                    {'\n'}Devices détectés: {devices && Object.keys(devices).length > 0
                      ? JSON.stringify(Object.keys(devices))
                      : 'Aucun dispositif détecté.'}
                    {'\n'}hasPermission: {String(hasPermission)}
                    {'\n'}Selected device: {device ? 'Available' : 'Not available'}
                  </Text>
                )}
              </View>
            ),
          },
        ]
      : []),
  ];

  return (
    <SafeAreaView style={styles.container}>
      <Header
        title="PillPall"
        onLogout={() => {
          logout();
          navigation.replace('Login');
        }}
        onHistory={() => navigation.navigate('Historique')}
        showMenu
      />
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 64 : 0}
      >
        <ErrorBoundary>
          <SectionList
            sections={sections}
            keyExtractor={(item, index) => `section-${index}`}
            renderItem={({ item, section }) => section.renderItem()}
            renderSectionHeader={({ section: { title } }) => (
              title !== 'Patient Picker' && title !== 'Actions' ? (
                <Text style={styles.sectionTitle}>{title}</Text>
              ) : null
            )}
            style={styles.content}
            contentContainerStyle={styles.sectionListContent}
            ListFooterComponent={
              <TouchableOpacity
                style={styles.floatingButton}
                onPress={() => {
                  console.log('Open manual form, mode:', 'manual');
                  setMode('manual');
                  setForm({
                    patientId: patientId,
                    medicationName: '',
                    dosage: '',
                    startDate: '',
                    endDate: '',
                    doseTimes: [{ hour: '', minute: '' }],
                  });
                  setEditingId(null);
                }}
              >
                <Text style={styles.buttonText}>+</Text>
              </TouchableOpacity>
            }
          />
        </ErrorBoundary>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flex: 1 },
  sectionListContent: { paddingTop: 16 },
  pickerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1E40AF',
    borderRadius: 8,
    marginBottom: 12,
    paddingHorizontal: 8,
    backgroundColor: '#F8F8F8',
    marginHorizontal: 16,
  },
  pickerIcon: { marginRight: 8 },
  picker: { flex: 1, color: '#000000' },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1E40AF',
    borderRadius: 8,
    marginBottom: 12,
    paddingHorizontal: 8,
    backgroundColor: '#F8F8F8',
    width: '100%', 
  },
  inputIcon: { marginRight: 8 },
  input: {
    flex: 1,
    padding: 10,
    fontSize: 16,
    color: '#000000',
  },
  doseTimeContainer: {
    flexDirection: 'row', 
    alignItems: 'center',
    marginBottom: 12,
    width: '100%', 
  },
  
 
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 16,
    marginHorizontal: 16,
  },
  blueButton: {
    backgroundColor: '#1E40AF',
    padding: 12,
    borderRadius: 6,
    alignItems: 'center',
    width: 150,
  },
  greenButton: {
    backgroundColor: '#10B981',
    padding: 12,
    borderRadius: 6,
    alignItems: 'center',
    marginTop: 8,
    marginHorizontal: 16,
    width: 'auto',
    minWidth: 150,
  },
  redButton: {
    backgroundColor: '#EF4444',
    padding: 8,
    borderRadius: 6,
    alignItems: 'center',
    marginTop: 8,
    marginHorizontal: 16,
    width: 'auto',
    minWidth: 100,
  },
  removeButton: {
    backgroundColor: '#EF4444',
    padding: 8,
    borderRadius: 6,
    alignItems: 'center',
    marginLeft: 8,
  },
  buttonText: { 
    color: '#FFFFFF', 
    textAlign: 'center', 
    fontWeight: '600',
    fontSize: 16,
  },
  redText: { 
    color: '#EF4444', 
    textAlign: 'center', 
    marginVertical: 8, 
    marginHorizontal: 16,
  },
  floatingButton: { 
    backgroundColor: '#1E40AF', 
    padding: 16, 
    borderRadius: 30, 
    alignItems: 'center', 
    margin: 16, 
    alignSelf: 'flex-end' 
  },
  preview: { 
    width: 300, 
    height: 200, 
    marginVertical: 8, 
    alignSelf: 'center',
  },
  sectionTitle: { 
    fontSize: 18, 
    fontWeight: 'bold', 
    color: '#1E40AF', 
    marginVertical: 12, 
    textAlign: 'center',
  },
  noPrescriptionsText: { 
    fontSize: 16, 
    color: '#666666', 
    textAlign: 'center', 
    marginVertical: 12, 
    marginHorizontal: 16 
  },
  formContainer: { 
    marginBottom: 16, 
    marginHorizontal: 16,
    alignItems: 'center',
  },
  scanContainer: { 
    alignItems: 'center', 
    justifyContent: 'center', 
    flex: 1,
  },
  camera: { 
    width: 300, 
    height: 300, 
    marginBottom: 12, 
    alignSelf: 'center',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
});

export default PrescriptionScreen;